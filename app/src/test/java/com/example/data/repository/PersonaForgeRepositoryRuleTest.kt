package com.example.data.repository

import android.os.Looper
import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows
import java.util.UUID

class PersonaForgeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObservePersona_authenticatedOwner_succeedsAndEmitsUpdate() = runBlocking(Dispatchers.IO) {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = PersonaForgeRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPersonaDraft(
                name = "Aria Vance",
                handle = "@ariavance",
                facialIdentityDescriptor = "High cheekbones, hazel almond eyes, beauty mark below left eye",
                referenceStorageUri = "vault://users/$aliceUid/refs/aria_1.jpg",
                referenceSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                consentSubjectName = "Aria Vance",
                consentVerified = true
            )
        }
        assertTrue(createResult.isSuccess)
        val personaId = createResult.getOrThrow()

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getPersonaById(aliceUid, personaId).getOrThrow()
        }
        assertEquals("Aria Vance", fetched.name)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun crossUserAccess_userACannotReadUserBPersona_failsWithPermissionDenied() = runBlocking(Dispatchers.IO) {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PersonaForgeRepository(firestore)
        val personaId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPersonaDraft(
                name = "Alice Secret Persona",
                handle = "@alicesecret",
                facialIdentityDescriptor = "Oval face, green eyes",
                referenceStorageUri = "vault://users/$aliceUid/refs/secret.jpg",
                referenceSha256 = "1122334455667788",
                consentSubjectName = "Alice Subject",
                consentVerified = true
            ).getOrThrow()
        }

        // Switch session to Bob
        signInTestUser(BOB_EMAIL)
        val bobRepo = PersonaForgeRepository(firestore)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getPersonaById(aliceUid, personaId)
        }
        assertTrue(crossReadResult.isFailure)
        val fsEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun getPersona_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking(Dispatchers.IO) {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = PersonaForgeRepository(firestore)
        val personaId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPersonaDraft(
                name = "Protected Persona",
                handle = "@protected",
                facialIdentityDescriptor = "Defined brow, hazel eyes",
                referenceStorageUri = "vault://users/$aliceUid/refs/prot.jpg",
                referenceSha256 = "abcdef1234567890",
                consentSubjectName = "Protected Subject",
                consentVerified = true
            ).getOrThrow()
        }

        auth.signOut()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val unauthResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getPersonaById(aliceUid, personaId)
        }
        assertTrue(unauthResult.isFailure)
        val fsEx = unauthResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun generationAndTwoStepPublishApproval_enforcesIdempotencyAndExactBinding() = runBlocking(Dispatchers.IO) {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = PersonaForgeRepository(firestore)

        val personaId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createPersonaDraft(
                name = "Mira Sol",
                handle = "@mirasol",
                facialIdentityDescriptor = "Defined jawline, warm amber eyes",
                referenceStorageUri = "vault://users/$aliceUid/refs/mira.jpg",
                referenceSha256 = "abcdef0123456789",
                consentSubjectName = "Mira Sol",
                consentVerified = true
            ).getOrThrow()
        }
        val persona = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getPersonaById(aliceUid, personaId).getOrThrow()
        }

        val idemKey = "idem_gen_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val asset1Result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.enqueueAndRecordGeneration(
                idempotencyKey = idemKey,
                persona = persona,
                jobType = "IMAGE_DRAFT",
                provider = "FIREBASE_AI_LOGIC",
                modelId = "gemini-nano-banana-2.1",
                prompt = "Editorial studio portrait with warm rim light",
                emotion = "Serene Confidence",
                stylePreset = "Cinema 50mm",
                estimatedCostMicros = 40000L,
                outputStorageUri = "vault://users/$aliceUid/assets/gen1.jpg",
                outputSha256Hash = "9988776655443322"
            )
        }
        assertTrue("Expected generation to succeed, got: ${asset1Result.exceptionOrNull()}", asset1Result.isSuccess)
        val asset1 = asset1Result.getOrThrow()

        // Duplicate call with same idempotencyKey returns exact same asset without double charging
        val assetDup = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.enqueueAndRecordGeneration(
                idempotencyKey = idemKey,
                persona = persona,
                jobType = "IMAGE_DRAFT",
                provider = "FIREBASE_AI_LOGIC",
                modelId = "gemini-nano-banana-2.1",
                prompt = "Editorial studio portrait with warm rim light",
                emotion = "Serene Confidence",
                stylePreset = "Cinema 50mm",
                estimatedCostMicros = 40000L,
                outputStorageUri = "vault://users/$aliceUid/assets/gen1.jpg",
                outputSha256Hash = "9988776655443322"
            ).getOrThrow()
        }
        assertEquals(asset1.id, assetDup.id)

        // Approve asset before scheduling
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.reviewAsset(asset1, "APPROVED", "Verified facial lock").getOrThrow()
        }
        val approvedAsset = asset1.copy(reviewStatus = "APPROVED")

        val scheduleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.prepareContentSchedule(
                asset = approvedAsset,
                destinationPlatform = "Patreon",
                scheduledEpochMillis = 1893456000000L,
                caption = "Official studio drop #1"
            ).getOrThrow()
        }

        val scheduleSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("schedules").document(scheduleId).get().await()
        }
        val schedule = checkNotNull(scheduleSnap.toObject(com.example.data.model.ScheduledPost::class.java))

        val approval = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.requestPublishApproval(schedule, requestedByRole = "CHATGPT_MCP").getOrThrow()
        }

        // Publishing before owner approves fails
        val prematurePub = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.publishApprovedPost(
                idempotencyKey = "idem_pub_premature",
                approvalId = approval.id,
                scheduleId = schedule.id,
                assetId = approvedAsset.id,
                destinationPlatform = "Patreon",
                scheduledEpochMillis = 1893456000000L,
                caption = "Official studio drop #1"
            )
        }
        assertTrue(prematurePub.isFailure)

        // Owner approves
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.decidePublishApprovalByOwner(approval, "APPROVED", "Owner verified").getOrThrow()
        }

        // Publishing with tampered caption fails bindingHash check
        val tamperedPub = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.publishApprovedPost(
                idempotencyKey = "idem_pub_tampered",
                approvalId = approval.id,
                scheduleId = schedule.id,
                assetId = approvedAsset.id,
                destinationPlatform = "Patreon",
                scheduledEpochMillis = 1893456000000L,
                caption = "Tampered unauthorized caption"
            )
        }
        assertTrue(tamperedPub.isFailure)

        // Publishing with exact approved parameters succeeds
        val validPub = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.publishApprovedPost(
                idempotencyKey = "idem_pub_valid_1",
                approvalId = approval.id,
                scheduleId = schedule.id,
                assetId = approvedAsset.id,
                destinationPlatform = "Patreon",
                scheduledEpochMillis = 1893456000000L,
                caption = "Official studio drop #1"
            )
        }
        assertTrue("Expected publish to succeed, got: ${validPub.exceptionOrNull()}", validPub.isSuccess)
        assertEquals("PUBLISHED", validPub.getOrThrow().status)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val ALICE_EMAIL = "alice_studio@test.com"
        const val BOB_EMAIL = "bob_studio@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
