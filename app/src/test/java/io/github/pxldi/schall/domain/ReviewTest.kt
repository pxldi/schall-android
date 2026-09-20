package io.github.pxldi.schall.domain

import io.github.pxldi.schall.data.AcquiredCopy
import io.github.pxldi.schall.data.AcquiredCopyEvidence
import io.github.pxldi.schall.data.AcquisitionTarget
import io.github.pxldi.schall.data.CopyGroup
import io.github.pxldi.schall.data.DownloadRequest
import io.github.pxldi.schall.data.ImportCandidate
import io.github.pxldi.schall.data.ImportEvidence
import io.github.pxldi.schall.data.ImportFileEvidence
import io.github.pxldi.schall.data.ImportTags
import io.github.pxldi.schall.data.ReviewItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewTest {
    private fun want(kind: String, id: String = "w1") = ReviewItem(kind = kind, target = AcquisitionTarget(id = id, title = "Song"))

    @Test
    fun a_resolution_is_a_version_question_and_anything_else_is_downloaded() {
        val out = questions(listOf(want("resolution", "a"), want("copies", "b"), want("stopped", "c")), null)
        assertEquals(listOf("Version", "Downloaded", "Downloaded"), out.map { it.label })
        assertEquals(listOf("want:a", "want:b", "want:c"), out.map { it.id })
    }

    @Test
    fun a_download_is_a_folder_question_only_while_it_carries_evidence() {
        val paused = DownloadRequest(id = "d1", importEvidence = ImportEvidence())
        val plain = DownloadRequest(id = "d2")
        val out = questions(null, listOf(paused, plain))
        assertEquals(listOf("download:d1"), out.map { it.id })
    }

    private fun copy(id: String, verdict: String = "held", bitRate: Int? = null, size: Long? = null) =
        AcquiredCopy(id = id, verdict = verdict, sizeBytes = size, evidence = bitRate?.let { AcquiredCopyEvidence(bitRate = it) })

    @Test
    fun copies_the_server_groups_read_as_one_card_led_by_the_best_rip() {
        val copies = listOf(copy("low", bitRate = 128), copy("high", bitRate = 320), copy("other"))
        val views = groupCopies(copies, listOf(CopyGroup(copyIds = listOf("low", "high"), rips = 2)))
        assertEquals(listOf("high", "other"), views.map { it.best.id })
        assertEquals(listOf("low"), views[0].others.map { it.id })
        assertEquals(2, views[0].rips)
    }

    @Test
    fun a_queue_without_groups_gives_one_card_per_copy_held_first() {
        val copies = listOf(copy("refused", verdict = "discarded_audio"), copy("kept"))
        val views = groupCopies(copies, null)
        assertEquals(listOf("kept", "refused"), views.map { it.best.id })
    }

    @Test
    fun the_catalogue_is_every_track_the_evidence_names_in_playing_order() {
        val evidence = ImportEvidence(
            files = listOf(
                ImportFileEvidence(name = "b.flac", expected = ImportTags(trackId = "t2", trackNumber = 2, title = "Two")),
                ImportFileEvidence(name = "a.flac", expected = ImportTags(trackId = "t1", trackNumber = 1, title = "One")),
            ),
            unmatchedTracks = listOf(ImportTags(trackId = "t3", discNumber = 2, trackNumber = 1, title = "Three")),
        )
        assertEquals(listOf("t1", "t2", "t3"), importCatalogue(evidence).map { it.trackId })
        assertEquals("2-1. Three", trackLabel(importCatalogue(evidence)[2]))
    }

    @Test
    fun files_with_problems_lead() {
        val fine = ImportFileEvidence(name = "fine")
        val odd = ImportFileEvidence(name = "odd", problems = listOf("no match"))
        assertEquals(listOf("odd", "fine"), orderFiles(listOf(fine, odd)).map { it.name })
    }

    @Test
    fun the_suggested_track_is_the_expected_one_then_a_free_candidate_then_the_first() {
        val catalogue = listOf(ImportTags(trackId = "t1"), ImportTags(trackId = "t2"))
        val taken = ImportCandidate(track = ImportTags(trackId = "t1"), takenBy = "other.flac")
        val free = ImportCandidate(track = ImportTags(trackId = "t2"))
        assertEquals("t9", suggestedTrack(ImportFileEvidence(name = "x", expected = ImportTags(trackId = "t9")), catalogue))
        assertEquals("t2", suggestedTrack(ImportFileEvidence(name = "x", candidates = listOf(taken, free)), catalogue))
        assertEquals("t1", suggestedTrack(ImportFileEvidence(name = "x"), catalogue))
    }

    @Test
    fun a_download_is_named_by_its_release_or_by_the_entry_it_looks_for() {
        assertEquals("Album" to "Artist", downloadTitle(DownloadRequest(id = "d", albumTitle = "Album", artistName = "Artist")))
        assertEquals("Song" to "Singer", downloadTitle(DownloadRequest(id = "d", entryTitle = "Song", entryArtist = "Singer", directory = "dir")))
        assertEquals("dir" to "", downloadTitle(DownloadRequest(id = "d", directory = "dir")))
    }
}
