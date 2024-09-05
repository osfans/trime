// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import java.lang.ref.WeakReference

class WeakHashSetTest :
    StringSpec({

        "add returns true when element is not in the set" {
            val set = WeakHashSet<Int>()
            set.add(1) shouldBe true
        }

        "add returns false when element is already in the set" {
            val set = WeakHashSet<Int>()
            set.add(1)
            set.add(1) shouldBe false
        }

        "remove returns true when element is in the set" {
            val set = WeakHashSet<Int>()
            set.add(1)
            set.remove(1) shouldBe true
        }

        "remove returns false when element is not in the set" {
            val set = WeakHashSet<Int>()
            set.remove(1) shouldBe false
        }

        "contains returns true when element is in the set" {
            val set = WeakHashSet<Int>()
            set.add(1)
            set.contains(1) shouldBe true
        }

        "contains returns false when element is not in the set" {
            val set = WeakHashSet<Int>()
            set.contains(1) shouldBe false
        }

        "isEmpty returns true when set is empty" {
            val set = WeakHashSet<Int>()
            set.isEmpty() shouldBe true
        }

        "isEmpty returns false when set is not empty" {
            val set = WeakHashSet<Int>()
            set.add(1)
            set.isEmpty() shouldBe false
        }

        "size returns correct size of the set" {
            val set = WeakHashSet<Int>()
            set.add(1)
            set.add(2)
            set.size shouldBe 2
        }

        "clear removes all elements from the set" {
            val set = WeakHashSet<Int>()
            set.add(1)
            set.add(2)
            set.clear()
            set.isEmpty() shouldBe true
        }

        "iterator should return all elements in the set" {
            val set = WeakHashSet<Int>()
            set.add(114)
            set.add(514)
            set.add(1919)
            set.add(810)
            val iterator = set.iterator()
            iterator.asSequence().toList().let {
                it shouldContainOnly listOf(114, 514, 1919, 810)
            }
        }

        "equals should work correctly" {
            val set1 = WeakHashSet<Int>()
            set1.add(1)
            set1.add(2)
            set1.add(3)
            set1.add(4)
            val set2 = WeakHashSet<Int>()
            set2.add(4)
            set2.add(2)
            set2.add(3)
            set2.add(1)
            set1 shouldBe set2
        }

        "addAll, removeAll and retainAll should work correctly" {
            val set1 = WeakHashSet<Int>()
            set1.add(1)
            set1.add(2)
            val set2 = WeakHashSet<Int>()
            set2.add(2)
            set2.add(3)
            set1.addAll(set2)
            set1.size shouldBe 3
            set1.removeAll(set2)
            set1.size shouldBe 1
            set1.add(2)
            set1.retainAll(set2)
            set1.size shouldBe 1
        }

        "should not prevent garbage collection" {
            val set = WeakHashSet<Any>()
            var canary: Any? = Any()
            val ref = WeakReference(canary)
            set.add(canary!!)
            @Suppress("UNUSED_VALUE")
            canary = null
            System.gc()
            ref.get() shouldBe null
        }
    })
