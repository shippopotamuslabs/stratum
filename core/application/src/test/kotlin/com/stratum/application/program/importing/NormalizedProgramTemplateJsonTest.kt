package com.stratum.application.program.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NormalizedProgramTemplateJsonTest {

    @Test
    fun `parses a full template with program name, cycle length, workouts, lifts, sets`() {
        val json = """
            {"programName":"5 Day Upper/Lower","cycleLength":9,
             "workouts":[
               {"name":"Upper A","weekday":null,"position":0,
                "lifts":[
                  {"name":"Low Incline Dumbbell Press","position":0,"supersetGroupKey":"1",
                   "sets":[
                     {"position":0,"setTypeLabel":"Standard Set","repMin":6,"repMax":8,"rirGoal":1,"restSeconds":30,"isTopSet":true},
                     {"position":1,"setTypeLabel":"Failure Set","repMin":1,"repMax":1,"rirGoal":0,"restSeconds":0,"isTopSet":false}
                   ]}
                ]}
             ]}
        """.trimIndent()

        val template = NormalizedProgramTemplateJson.parse(json)

        assertEquals("5 Day Upper/Lower", template.programName)
        assertEquals(9, template.cycleLength)
        assertEquals(1, template.workouts.size)
        assertEquals("Upper A", template.workouts[0].name)
        assertNull(template.workouts[0].weekday)
        val lift = template.workouts[0].lifts[0]
        assertEquals("Low Incline Dumbbell Press", lift.name)
        assertEquals("1", lift.supersetGroupKey)
        assertEquals(2, lift.sets.size)
        assertEquals("Standard Set", lift.sets[0].setTypeLabel)
        assertEquals(6, lift.sets[0].repMin)
        assertEquals(8, lift.sets[0].repMax)
        assertEquals(1, lift.sets[0].rirGoal)
        assertEquals(30, lift.sets[0].restSeconds)
        assertTrue(lift.sets[0].isTopSet)
        assertEquals("Failure Set", lift.sets[1].setTypeLabel)
        assertEquals(0, lift.sets[1].restSeconds)
    }

    @Test
    fun `parses null fields`() {
        val json = """{"programName":null,"cycleLength":null,"workouts":[{"name":"Day 1","weekday":null,"position":0,"lifts":[{"name":"Squat","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":null,"repMax":null,"rirGoal":null,"restSeconds":null,"isTopSet":true}],"supersetGroupKey":null}]}]}"""
        val template = NormalizedProgramTemplateJson.parse(json)
        assertNull(template.programName)
        assertNull(template.cycleLength)
        assertNull(template.workouts[0].lifts[0].sets[0].setTypeLabel)
        assertNull(template.workouts[0].lifts[0].sets[0].repMin)
        assertNull(template.workouts[0].lifts[0].supersetGroupKey)
    }

    @Test
    fun `parses an empty workouts array`() {
        val json = """{"programName":"Empty","cycleLength":1,"workouts":[]}"""
        val template = NormalizedProgramTemplateJson.parse(json)
        assertTrue(template.workouts.isEmpty())
    }

    @Test
    fun `unescapes unicode in lift names`() {
        val json = """{"programName":"P","cycleLength":null,"workouts":[{"name":"D1","weekday":null,"position":0,"lifts":[{"name":"Skull\u0020Crusher","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":8,"repMax":12,"rirGoal":2,"restSeconds":90,"isTopSet":true}],"supersetGroupKey":null}]}]}"""
        val template = NormalizedProgramTemplateJson.parse(json)
        assertEquals("Skull Crusher", template.workouts[0].lifts[0].name)
    }

    @Test
    fun `defaults missing position fields to the element index`() {
        val json = """{"programName":null,"cycleLength":null,"workouts":[{"name":"D1","weekday":null,"position":0,"lifts":[{"name":"A","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":5,"repMax":5,"rirGoal":1,"restSeconds":120,"isTopSet":true}],"supersetGroupKey":null}]}]}"""
        val template = NormalizedProgramTemplateJson.parse(json)
        assertEquals(0, template.workouts[0].position)
        assertEquals(0, template.workouts[0].lifts[0].position)
    }
}
