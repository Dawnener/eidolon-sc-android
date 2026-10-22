package sc.eidolon.nativeapp.model

/** Local, immediate updates after a gift/draw, before the server state is fetched again. */
object GiftUpdates {
    fun withDrawn(state: GiftState, result: DrawOutcome): GiftState {
        val counts = state.backpack.associate { it.giftId to it.count }.toMutableMap()
        result.drawn.forEach { item ->
            if (item is DrawItem.Gift) {
                counts[item.giftId] = (counts[item.giftId] ?: 0) + 1
            }
        }
        val order = buildList {
            addAll(state.backpack.map { it.giftId })
            addAll(counts.keys.filter { id -> state.backpack.none { it.giftId == id } })
        }
        val backpack = order.mapNotNull { id ->
            counts[id]?.let { BackpackItem(id, it) }
        }
        val drawnResetCards = result.drawn.count { it is DrawItem.ResetCard }
        val reward = result.rewarded
        return state.copy(
            draws = result.draws,
            backpack = backpack,
            resetCards = state.resetCards + drawnResetCards + (reward?.resetCards ?: 0),
            dailyTasks = state.dailyTasks?.let { withDailyDone(it, reward?.dailyTasks.orEmpty()) },
            rewardedAt = reward?.rewardedAt ?: state.rewardedAt,
        )
    }

    fun afterGiving(
        state: GiftState,
        characterId: String,
        giftId: String,
        given: GivenGift,
    ): GiftState {
        val reward = given.rewarded
        val updatedCharacter = given.affinity?.let { affinity ->
            GiftCharacter(
                id = characterId,
                tier = affinity.tier,
                cooling = affinity.cooldownUntil != null,
                cooldownUntil = affinity.cooldownUntil,
            )
        }
        return state.copy(
            backpack = state.backpack.mapNotNull { stack ->
                when {
                    stack.giftId != giftId -> stack
                    given.left <= 0 -> null
                    else -> stack.copy(count = given.left)
                }
            },
            draws = (state.draws + (reward?.draws ?: 0)).coerceAtLeast(0),
            resetCards = (state.resetCards + (reward?.resetCards ?: 0)).coerceAtLeast(0),
            characters = state.characters.map { character ->
                if (character.id == characterId && updatedCharacter != null) {
                    updatedCharacter
                } else {
                    character
                }
            },
            dailyTasks = state.dailyTasks?.let { withDailyDone(it, reward?.dailyTasks.orEmpty()) },
            rewardedAt = reward?.rewardedAt ?: state.rewardedAt,
        )
    }

    fun withDailyDone(tasks: DailyTasks, doneIds: Collection<String>): DailyTasks {
        if (doneIds.isEmpty()) return tasks
        return tasks.copy(
            tasks = tasks.tasks.map { task ->
                if (task.id in doneIds) {
                    task.copy(done = true, progress = task.goal.coerceAtLeast(task.progress))
                } else {
                    task
                }
            },
        )
    }
}
