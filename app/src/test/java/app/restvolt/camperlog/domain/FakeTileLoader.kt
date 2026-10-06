package app.restvolt.camperlog.domain

/**
 * [TileLoader]-Fake für Tests ohne echtes Netzwerk. Liefert standardmäßig [TileLoadResult.Failure]:
 * UI-Flow-Tests prüfen die Marker-/Listensemantik, nicht das gezeichnete Kachelbild, daher spart das
 * ein unnötiges PNG-Dekodieren unter Robolectric.
 */
class FakeTileLoader(private val result: TileLoadResult = TileLoadResult.Failure) : TileLoader {
    val requests = mutableListOf<TileCoord>()

    override suspend fun load(tile: TileCoord): TileLoadResult {
        requests.add(tile)
        return result
    }
}
