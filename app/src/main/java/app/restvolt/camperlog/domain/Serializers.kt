package app.restvolt.camperlog.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency

/** Speichert ein Datum als ISO-Text, z. B. `2026-10-02`. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.restvolt.camperlog.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Speichert eine Uhrzeit als ISO-Text, z. B. `18:40`. */
object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.restvolt.camperlog.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

/** Speichert eine Währung als ISO-4217-Code, z. B. `CHF`. */
object CurrencySerializer : KSerializer<Currency> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("app.restvolt.camperlog.Currency", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Currency) = encoder.encodeString(value.currencyCode)

    override fun deserialize(decoder: Decoder): Currency = Currency.getInstance(decoder.decodeString())
}
