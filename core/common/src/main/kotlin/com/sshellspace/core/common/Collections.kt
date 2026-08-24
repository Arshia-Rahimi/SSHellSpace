package com.sshellspace.core.common

import kotlin.reflect.KClass

fun <T : Any> Iterable<*>.firstIsInstanceOrNull(klass: KClass<T>): T? =
    filterIsInstance(klass.java).firstOrNull()
