package com.gasstation.core.database.station

data class StationBucketSnapshot(val marker: StationCacheSnapshotEntity?, val rows: List<StationCacheEntity>)
