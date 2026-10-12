package app.restvolt.camperlog.domain

/**
 * Schwärzt Diagnoseprotokoll-Einträge vor dem Schreiben – das Protokoll kann das Gerät über
 * "Fehlerprotokoll teilen" verlassen, daher darf keine Koordinate, kein Geldbetrag, kein
 * Kennzeichen und kein Dateipfad mit echtem Nutzernamen je auf die Platte kommen.
 */
object DiagnosticsLogRedactor {

    // Koordinaten-förmige Dezimalzahlen (4+ Nachkommastellen, die Genauigkeit, mit der diese
    // App eigene GPS-Fixes speichert, siehe TrackPoint) → [COORD]. Bewusst breit: eine zu Unrecht
    // geschwärzte, unverbundene Zahl mit vielen Nachkommastellen ist ein viel billigerer Fehler
    // als eine echte Koordinate durchzulassen.
    private val coordinatePattern = Regex("""-?\d{1,3}\.\d{4,}""")

    // Geldbeträge, Betrag vor oder nach dem Währungszeichen: "12,34 €", "€12.34", "1.234,56 EUR".
    private val moneySuffixPattern = Regex("""\d{1,3}(?:[.,]\d{3})*[.,]\d{2}\s?(?:€|EUR|USD|CHF|\$)""")
    private val moneyPrefixPattern = Regex("""(?:€|EUR|USD|CHF|\$)\s?\d{1,3}(?:[.,]\d{3})*[.,]\d{2}""")

    // Deutsches Kfz-Kennzeichen-Muster, z. B. "M AB 1234".
    private val platePattern = Regex("""\b[A-ZÄÖÜ]{1,3}[ -][A-Z]{1,2}[ -]?\d{1,4}[EH]?\b""")

    // Dateipfade, die einen echten Nutzernamen tragen; der eigene Sandbox-Pfad der App
    // (/data/user/0/app.restvolt.camperlog/…) bleibt bewusst unangetastet – der nennt niemanden
    // und ist genau das Detail, für das ein Diagnoseprotokoll existiert.
    private val pathPattern = Regex(
        """content://\S+""" + "|" +
            """/storage/emulated/\d+/\S*""" + "|" +
            """/sdcard/\S*""" + "|" +
            """/Users/[^/\s]+(?:/\S*)?""" + "|" +
            """/home/[^/\s]+(?:/\S*)?""",
    )

    /** Wendet alle Muster oben auf [text] an (spezifischere Muster vor dem breiten Koordinaten-Muster) und liefert das Ergebnis. */
    fun redact(text: String): String {
        var result = text
        result = result.replace(pathPattern, "[PATH]")
        result = result.replace(moneySuffixPattern, "[AMOUNT]")
        result = result.replace(moneyPrefixPattern, "[AMOUNT]")
        result = result.replace(platePattern, "[PLATE]")
        result = result.replace(coordinatePattern, "[COORD]")
        return result
    }
}
