package com.example.ourmemories.data.models

import com.example.ourmemories.R

data class TreeInfo(
    val levelNameResId: Int, val iconRes: Int, val currentPoints: Long, val maxPoints: Long
) {
    companion object {
        fun getTreeInfo(points: Long): TreeInfo {
            val (nameResId, iconRes, maxPoints) = when {
                points >= 1000 -> Triple(
                    R.string.tree_stage_eternal, R.drawable.ic_tree_stage_10, 2000L
                )

                points >= 800 -> Triple(
                    R.string.tree_stage_magic, R.drawable.ic_tree_stage_9, 1000L
                )

                points >= 650 -> Triple(
                    R.string.tree_stage_abundant, R.drawable.ic_tree_stage_8, 800L
                )

                points >= 500 -> Triple(R.string.tree_stage_love, R.drawable.ic_tree_stage_7, 650L)
                points >= 350 -> Triple(
                    R.string.tree_stage_blooming, R.drawable.ic_tree_stage_6, 500L
                )

                points >= 200 -> Triple(
                    R.string.tree_stage_mature, R.drawable.ic_tree_stage_5, 350L
                )

                points >= 150 -> Triple(
                    R.string.tree_stage_strong, R.drawable.ic_tree_stage_4, 200L
                )

                points >= 100 -> Triple(R.string.tree_stage_young, R.drawable.ic_tree_stage_3, 150L)
                points >= 50 -> Triple(R.string.tree_stage_sapling, R.drawable.ic_tree_stage_2, 50L)
                else -> Triple(R.string.tree_stage_sprout, R.drawable.ic_tree_stage_1, 20L)
            }
            return TreeInfo(nameResId, iconRes, points, maxPoints)
        }
    }
}