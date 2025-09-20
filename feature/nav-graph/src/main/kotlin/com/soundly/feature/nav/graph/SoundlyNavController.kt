package com.soundly.feature.nav.graph

import androidx.navigation.NavController

interface SoundlyNavController<T> {
    fun navigate(navController: NavController, navInfo: T)
}