package com.semo.keyboard.domain.logic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer

object NeuralLm {
    private const val MODEL_ASSET = "ai/ruq-5m.onnx"
    private const val TOKENIZER_ASSET = "ai/tokenizer.json"
    private const val MODEL_FILE = "ai_ruq-5m_v1.onnx"
    private const val MAX_CONTEXT_TOKENS = 28
    private const val BEAMS = 5
    private const val EXTRA_TOKENS = 3
    private const val MIN_PROB = 0.03f
    private const val BOS = 1
    private const val SPECIAL_COUNT = 6
    private const val SPACE_MARK = '\u0120'
    private val PRE = Regex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")
    /** the tokenizer was trained on text without diacritics and tatweel, so they are removed before encoding */
    private val DIACRITICS = Regex("[\\u064B-\\u0652\\u0670\\u0653-\\u0655\\u0640]")
    private val WORD = Regex("^[\\u0621-\\u064A]{2,}$")

    private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null
    private var inputName = "input_ids"
    private var vocab = HashMap<String, Int>()
    private var tokens = arrayOfNulls<String>(0)
    private var ranks = HashMap<String, Int>()
    private var wordStart = BooleanArray(0)
    private var plainPiece = BooleanArray(0)
    private val byteToChar = CharArray(256)
    private val charToByte = HashMap<Char, Int>()
    private val cache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 64
    }

    @Volatile
    var ready = false
        private set

    /** Why the model is off (null = no problem). Shown by the diagnostics page. */
    @Volatile
    var loadError: String? = null
        private set

    @Synchronized
    fun load(context: Context) {
        if (ready) return
        loadError = null
        try {
            val tokJson = context.assets.open(TOKENIZER_ASSET).bufferedReader().use { it.readText() }
            val modelFile = File(context.filesDir, MODEL_FILE)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                val tmp = File(context.filesDir, "$MODEL_FILE.tmp")
                context.assets.open(MODEL_ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(modelFile)) return
            }
            buildByteMaps()
            parseTokenizer(tokJson)
            val e = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            opts.setIntraOpNumThreads(2)
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val s = e.createSession(modelFile.absolutePath, opts)
            // the tokenizer must be the one the model was trained with: every token id has to be a valid embedding row,
            // otherwise ONNX Runtime fails on every query ("indices element out of data bounds") and no suggestion is ever produced
            val modelVocab = runCatching {
                (s.outputInfo.values.first().info as ai.onnxruntime.TensorInfo).shape.last().toInt()
            }.getOrDefault(-1)
            if (modelVocab > 0 && vocab.size != modelVocab) {
                loadError = "tokenizer.json has ${vocab.size} tokens but the model has $modelVocab; use the tokenizer.json of the same model (Ruqiya/ruqlm)"
                runCatching { s.close() }
                ready = false
                session = null
                return
            }
            env = e
            inputName = s.inputNames.first()
            session = s
            synchronized(cache) { cache.clear() }
            ready = true
        } catch (t: Throwable) {
            loadError = t.javaClass.simpleName + ": " + (t.message ?: "")
            ready = false
            session = null
        }
    }

    @Synchronized
    fun unload() {
        ready = false
        runCatching { session?.close() }
        session = null
        synchronized(cache) { cache.clear() }
    }

    fun nextWords(context: List<String>, limit: Int): List<String> {
        if (!ready || limit <= 0) return emptyList()
        val words = context.filter { it.isNotBlank() }.takeLast(4)
        if (words.isEmpty()) return emptyList()
        val key = words.joinToString(" ")
        synchronized(cache) { cache[key]?.let { return it.take(limit) } }
        val result = try {
            compute(words)
        } catch (t: Throwable) {
            emptyList()
        }
        synchronized(cache) { cache[key] = result }
        return result.take(limit)
    }

    private fun compute(words: List<String>): List<String> {
        val base = ArrayList<Int>()
        base.add(BOS)
        for (id in encode(words.joinToString(" "))) base.add(id)
        val ctx: List<Int> = if (base.size > MAX_CONTEXT_TOKENS) listOf(BOS) + base.takeLast(MAX_CONTEXT_TOKENS - 1) else base
        val first = logits(ctx.toIntArray()) ?: return emptyList()
        val probs = softmax(first)
        val starts = ArrayList<Int>()
        for (i in first.indices) if (i < wordStart.size && wordStart[i]) starts.add(i)
        starts.sortByDescending { first[it] }
        val out = LinkedHashMap<String, Float>()
        for (id in starts.take(BEAMS)) {
            val p = probs[id]
            if (p < MIN_PROB) break
            val seq = ArrayList<Int>(ctx)
            seq.add(id)
            val piece = arrayListOf(id)
            var complete = false
            for (step in 0..EXTRA_TOKENS) {
                val lg = logits(seq.toIntArray()) ?: break
                var best = -1
                var bv = Float.NEGATIVE_INFINITY
                for (j in SPECIAL_COUNT until lg.size) {
                    if (lg[j] > bv) {
                        bv = lg[j]
                        best = j
                    }
                }
                if (best < 0 || best >= plainPiece.size || !plainPiece[best]) {
                    complete = true
                    break
                }
                if (step == EXTRA_TOKENS) break
                seq.add(best)
                piece.add(best)
            }
            if (!complete) continue
            val w = decode(piece).trim()
            if (WORD.matches(w)) out[w] = maxOf(out[w] ?: 0f, p)
        }
        return out.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun logits(ids: IntArray): FloatArray? {
        val s = session ?: return null
        val e = env ?: return null
        val arr = LongArray(ids.size) { ids[it].toLong() }
        OnnxTensor.createTensor(e, LongBuffer.wrap(arr), longArrayOf(1, arr.size.toLong())).use { t ->
            s.run(mapOf(inputName to t)).use { res ->
                val v = res[0].value as Array<*>
                return v[0] as FloatArray
            }
        }
    }

    private fun softmax(x: FloatArray): FloatArray {
        var m = Float.NEGATIVE_INFINITY
        for (v in x) if (v > m) m = v
        var sum = 0f
        val out = FloatArray(x.size)
        for (i in x.indices) {
            out[i] = Math.exp((x[i] - m).toDouble()).toFloat()
            sum += out[i]
        }
        if (sum > 0f) for (i in out.indices) out[i] = out[i] / sum
        return out
    }

    private fun encode(text: String): IntArray {
        val clean = DIACRITICS.replace(text, "")
        val t = if (clean.startsWith(" ")) clean else " $clean"
        val out = ArrayList<Int>()
        for (m in PRE.findAll(t)) {
            val bytes = m.value.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder(bytes.size)
            for (b in bytes) sb.append(byteToChar[b.toInt() and 0xFF])
            for (piece in bpe(sb.toString())) out.add(vocab[piece] ?: 3)
        }
        return out.toIntArray()
    }

    private fun bpe(word: String): List<String> {
        val parts = ArrayList<String>(word.length)
        for (c in word) parts.add(c.toString())
        while (parts.size > 1) {
            var best = Int.MAX_VALUE
            var idx = -1
            for (i in 0 until parts.size - 1) {
                val r = ranks[parts[i] + " " + parts[i + 1]] ?: continue
                if (r < best) {
                    best = r
                    idx = i
                }
            }
            if (idx < 0) break
            val a = parts[idx]
            val b = parts[idx + 1]
            val merged = a + b
            var i = 0
            while (i < parts.size - 1) {
                if (parts[i] == a && parts[i + 1] == b) {
                    parts[i] = merged
                    parts.removeAt(i + 1)
                }
                i++
            }
        }
        return parts
    }

    private fun decode(ids: List<Int>): String {
        val sb = StringBuilder()
        for (id in ids) sb.append(tokens.getOrNull(id) ?: "")
        val bytes = ByteArray(sb.length)
        for (i in 0 until sb.length) bytes[i] = (charToByte[sb[i]] ?: 63).toByte()
        return String(bytes, Charsets.UTF_8)
    }

    private fun buildByteMaps() {
        val bs = ArrayList<Int>()
        for (b in 33..126) bs.add(b)
        for (b in 161..172) bs.add(b)
        for (b in 174..255) bs.add(b)
        val cs = ArrayList<Int>(bs)
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        charToByte.clear()
        for (i in bs.indices) {
            byteToChar[bs[i]] = cs[i].toChar()
            charToByte[cs[i].toChar()] = bs[i]
        }
    }

    private fun parseTokenizer(json: String) {
        val model = JSONObject(json).getJSONObject("model")
        val v = model.getJSONObject("vocab")
        val map = HashMap<String, Int>(v.length() * 2)
        var maxId = 0
        val keys = v.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = v.getInt(k)
            map[k] = id
            if (id > maxId) maxId = id
        }
        val arr = arrayOfNulls<String>(maxId + 1)
        for ((k, id) in map) arr[id] = k
        val mg = model.getJSONArray("merges")
        val rk = HashMap<String, Int>(mg.length() * 2)
        for (i in 0 until mg.length()) {
            val e = mg.get(i)
            val key = if (e is JSONArray) e.getString(0) + " " + e.getString(1) else e.toString()
            rk[key] = i
        }
        val ws = BooleanArray(arr.size)
        val pp = BooleanArray(arr.size)
        for (id in SPECIAL_COUNT until arr.size) {
            val tok = arr[id] ?: continue
            if (tok.isEmpty()) continue
            if (tok[0] == SPACE_MARK) {
                ws[id] = tok.length > 1
            } else {
                var ok = true
                for (c in tok) {
                    val b = charToByte[c]
                    if (b == null || b < 0x80) {
                        ok = false
                        break
                    }
                }
                pp[id] = ok
            }
        }
        vocab = map
        tokens = arr
        ranks = rk
        wordStart = ws
        plainPiece = pp
    }
}
