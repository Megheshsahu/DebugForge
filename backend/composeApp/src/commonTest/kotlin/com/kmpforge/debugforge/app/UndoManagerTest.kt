package com.kmpforge.debugforge.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoManagerTest {
    @Test
    fun testRecordAndUndoRedo() {
        val manager = UndoManager()
        val fix1 = manager.recordFix("file1.kt", "old1", "new1", "Fix 1")
        val fix2 = manager.recordFix("file2.kt", "old2", "new2", "Fix 2")

        // Undo stack should have 2 items
        assertEquals(2, manager.undoStack.value.size)
        assertTrue(manager.canUndo())
        assertFalse(manager.canRedo())

        // Undo last fix
        val undone = manager.popUndo()
        assertEquals(fix2.id, undone?.id)
        assertTrue(manager.canUndo())
        assertTrue(manager.canRedo())

        // Redo last undone fix
        val redone = manager.popRedo()
        assertEquals(fix2.id, redone?.id)
        assertTrue(manager.canUndo())
        assertFalse(manager.canRedo())
    }

    @Test
    fun testClearAll() {
        val manager = UndoManager()
        manager.recordFix("file1.kt", "old1", "new1", "Fix 1")
        manager.recordFix("file2.kt", "old2", "new2", "Fix 2")
        manager.clearAll()
        assertEquals(0, manager.undoStack.value.size)
        assertEquals(0, manager.redoStack.value.size)
    }
}
