/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.storage

class StorageAlreadyExistsException(message: String) : Exception(message)

class StorageNotFoundException(message: String) : Exception(message)
