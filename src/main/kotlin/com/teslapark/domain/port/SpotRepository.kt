package com.teslapark.domain.port

interface SpotRepository :
    SpotQuery,
    SpotAllocation,
    SpotSynchronization
