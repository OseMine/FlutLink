package com.flutcloud.flutlink.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Parse a possibly-null, possibly-string/number primitive to Long. */
fun JsonElement?.asLongOrNull(): Long? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> contentOrNull?.toLongOrNull()
    else -> null
}

/** Parse a possibly-null, possibly-string/number primitive to Double. */
fun JsonElement?.asDoubleOrNull(): Double? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> contentOrNull?.toDoubleOrNull()
    else -> null
}

/** Parse a possibly-null primitive to Boolean. */
fun JsonElement?.asBooleanOrNull(): Boolean? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> contentOrNull?.toBooleanStrictOrNull()
    else -> null
}