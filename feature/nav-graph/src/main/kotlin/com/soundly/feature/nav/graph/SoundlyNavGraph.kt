package com.soundly.feature.nav.graph

import androidx.navigation.NavGraphBuilder

interface SoundlyNavGraph<T> {
    fun buildNavGraph(navGraphBuilder: NavGraphBuilder, navInfo: T)
}