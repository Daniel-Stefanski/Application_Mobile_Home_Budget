package com.example.homebudget.data.sync

import com.example.homebudget.data.dao.PendingSyncDao
import com.example.homebudget.data.entity.PendingSync

object PendingSyncHelper {
    suspend fun enqueueOrMerge(
        dao: PendingSyncDao,
        newItem: PendingSync
    ) {
        val localId = newItem.localId
        if (localId == null) {
            dao.insert(newItem)
            return
        }

        val existing = dao.findByEntityTypeAndLocalId(newItem.entityType, localId)
        if (existing == null) {
            dao.insert(newItem)
            return
        }

        when (existing.operation) {
            SyncConstants.OP_INSERT -> {
                when (newItem.operation) {
                    SyncConstants.OP_UPDATE -> {
                        dao.update(
                            existing.copy(
                                payloadJson = newItem.payloadJson,
                                remoteId = newItem.remoteId ?: existing.remoteId
                            )
                        )
                    }

                    SyncConstants.OP_DELETE -> {
                        dao.delete(existing)
                    }

                    else -> Unit
                }
            }

            SyncConstants.OP_UPDATE -> {
                when (newItem.operation) {
                    SyncConstants.OP_UPDATE -> {
                        dao.update(
                            existing.copy(
                                payloadJson = newItem.payloadJson,
                                remoteId = newItem.remoteId ?: existing.remoteId
                            )
                        )
                    }

                    SyncConstants.OP_DELETE -> {
                        dao.update(
                            existing.copy(
                                operation = SyncConstants.OP_DELETE,
                                payloadJson = newItem.payloadJson,
                                remoteId = newItem.remoteId ?: existing.remoteId
                            )
                        )
                    }

                    else -> Unit
                }
            }

            SyncConstants.OP_DELETE -> {
                // Nic nie robimy - rekord i tak ma zostać usunięty
            }

            else -> {
                dao.insert(newItem)
            }
        }
    }
}