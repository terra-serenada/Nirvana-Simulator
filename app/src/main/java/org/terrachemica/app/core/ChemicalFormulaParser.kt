package org.terrachemica.app.core

/**
 * Parses ordinary condensed chemical formulas with nested parentheses and integer
 * subscripts, for example CO2, Mg2SiO4, Ca5(PO4)3OH and Mg3Si2O5(OH)4.
 * Ionic charges and hydrate-dot notation are intentionally not accepted here.
 */
object ChemicalFormulaParser {
    fun parse(formula: String): Map<String, Int> {
        require(formula.isNotBlank()) { "Formula kimia kosong" }
        val parser = Parser(formula)
        val result = parser.parseGroup(expectClosingParenthesis = false)
        require(parser.isAtEnd()) { "Karakter formula tidak dikenali pada '${formula}'" }
        require(result.isNotEmpty()) { "Formula tidak mengandung unsur" }
        return result.toSortedMap()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun isAtEnd(): Boolean = index >= source.length

        fun parseGroup(expectClosingParenthesis: Boolean): MutableMap<String, Int> {
            val result = linkedMapOf<String, Int>()
            while (!isAtEnd() && source[index] != ')') {
                if (source[index] == '(') {
                    index++
                    val nested = parseGroup(expectClosingParenthesis = true)
                    require(!isAtEnd() && source[index] == ')') {
                        "Kurung tutup tidak ditemukan pada '$source'"
                    }
                    index++
                    val multiplier = readMultiplier()
                    nested.forEach { (element, count) ->
                        result[element] = Math.addExact(result[element] ?: 0, Math.multiplyExact(count, multiplier))
                    }
                } else {
                    val element = readElement()
                    val multiplier = readMultiplier()
                    result[element] = Math.addExact(result[element] ?: 0, multiplier)
                }
            }
            if (expectClosingParenthesis) {
                require(!isAtEnd() && source[index] == ')') { "Kurung tutup tidak ditemukan pada '$source'" }
            } else {
                require(isAtEnd()) { "Kurung tutup berlebih pada '$source'" }
            }
            return result
        }

        private fun readElement(): String {
            require(!isAtEnd() && source[index] in 'A'..'Z') {
                "Simbol unsur tidak valid pada posisi $index dalam '$source'"
            }
            val start = index++
            if (!isAtEnd() && source[index] in 'a'..'z') index++
            return source.substring(start, index)
        }

        private fun readMultiplier(): Int {
            val start = index
            while (!isAtEnd() && source[index].isDigit()) index++
            if (start == index) return 1
            val parsed = source.substring(start, index).toInt()
            require(parsed > 0) { "Subskrip harus lebih besar dari nol pada '$source'" }
            return parsed
        }
    }
}
