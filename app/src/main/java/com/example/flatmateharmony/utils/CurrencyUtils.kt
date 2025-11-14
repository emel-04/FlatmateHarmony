package com.example.flatmateharmony.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Format số tiền thành chuỗi VND với dấu phân cách
 * Ví dụ: 1000000 -> "1.000.000"
 */
fun formatCurrency(amount: Long): String {
    val formatter = DecimalFormat("#,###")
    formatter.decimalFormatSymbols = formatter.decimalFormatSymbols.apply {
        groupingSeparator = '.'
    }
    return formatter.format(amount)
}

/**
 * Format số tiền thành chuỗi VND với đơn vị
 * Ví dụ: 1000000 -> "1.000.000đ"
 */
fun formatCurrencyWithUnit(amount: Long): String {
    return "${formatCurrency(amount)}đ"
}

/**
 * Loại bỏ dấu phân cách và chuyển thành số
 * Ví dụ: "1.000.000" -> 1000000
 */
fun parseCurrency(text: String): Long {
    val cleanString = text.replace(".", "").replace(",", "").replace("đ", "").trim()
    return cleanString.toLongOrNull() ?: 0L
}

/**
 * VisualTransformation để tự động format tiền khi nhập
 */
class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val digitsOnly = originalText.filter { it.isDigit() }
        
        if (digitsOnly.isEmpty()) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }
        
        val formatted = formatCurrency(digitsOnly.toLongOrNull() ?: 0L)
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                val digitsBeforeOffset = originalText.substring(0, offset.coerceAtMost(originalText.length))
                    .count { it.isDigit() }
                
                var transformedOffset = 0
                var digitCount = 0
                
                for (char in formatted) {
                    if (digitCount >= digitsBeforeOffset) break
                    transformedOffset++
                    if (char.isDigit()) digitCount++
                }
                
                return transformedOffset
            }
            
            override fun transformedToOriginal(offset: Int): Int {
                if (offset == 0) return 0
                val charsBeforeOffset = formatted.substring(0, offset.coerceAtMost(formatted.length))
                return charsBeforeOffset.count { it.isDigit() }
            }
        }
        
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Composable helper để sử dụng CurrencyVisualTransformation
 */
@Composable
fun rememberCurrencyVisualTransformation(): VisualTransformation {
    return remember { CurrencyVisualTransformation() }
}

