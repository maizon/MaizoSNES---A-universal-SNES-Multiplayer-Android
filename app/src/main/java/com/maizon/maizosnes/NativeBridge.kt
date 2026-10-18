package com.maizon.maizosnes

object NativeBridge {
    init {
        System.loadLibrary("native-lib")
    }

    external fun nativeInit(): Int
}
