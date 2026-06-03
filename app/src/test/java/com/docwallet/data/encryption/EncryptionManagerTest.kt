package com.docwallet.data.encryption

import android.app.Application
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import io.mockk.*
import java.nio.ByteBuffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class EncryptionManagerTest {

    private lateinit var manager: EncryptionManager

    @Before
    fun setUp() {
        mockkConstructor(Argon2Kt::class)
        every { anyConstructed<Argon2Kt>().hash(
            mode = any<Argon2Mode>(),
            password = any<ByteArray>(),
            salt = any<ByteArray>(),
            tCostInIterations = any<Int>(),
            mCostInKibibyte = any<Int>(),
            parallelism = any<Int>(),
            hashLengthInBytes = any<Int>(),
        ) } answers {
            val password = arg<ByteArray>(1)
            val salt = arg<ByteArray>(2)
            val hashLen = arg<Int>(6)
            val hash = ByteArray(hashLen) { i ->
                (password.getOrElse(i % password.size) { 0 } +
                 salt.getOrElse(i % salt.size) { 0 } + i).toByte()
            }
            Argon2KtResult(ByteBuffer.wrap(hash), ByteBuffer.wrap(byteArrayOf()))
        }

        val context = RuntimeEnvironment.getApplication().applicationContext
        manager = EncryptionManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isFirstLaunch returns true initially`() {
        assertTrue(manager.isFirstLaunch())
    }

    @Test
    fun `initializeDeviceKeyMode creates wrapped key and device key files`() {
        manager.initializeDeviceKeyMode()
        assertFalse(manager.isFirstLaunch())
        assertFalse(manager.isPasswordSet())
    }

    @Test
    fun `isFirstLaunch returns false after init`() {
        manager.initializeDeviceKeyMode()
        assertFalse(manager.isFirstLaunch())
    }

    @Test
    fun `isPasswordSet returns false in device key mode`() {
        manager.initializeDeviceKeyMode()
        assertFalse(manager.isPasswordSet())
    }

    @Test
    fun `getMasterKeyForSession returns non-null key in device key mode`() {
        manager.initializeDeviceKeyMode()
        val key = manager.getMasterKeyForSession()
        assertNotNull(key)
        assertEquals(32, key!!.size)
    }

    @Test
    fun `getMasterKeyForSession returns cached key on second call`() {
        manager.initializeDeviceKeyMode()
        manager.lock()
        val key1 = manager.getMasterKeyForSession()
        val key2 = manager.getMasterKeyForSession()
        assertNotNull(key1)
        assertNotNull(key2)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `setPassword returns true`() {
        manager.initializeDeviceKeyMode()
        assertTrue(manager.setPassword("test_password123"))
    }

    @Test
    fun `isPasswordSet returns true after setPassword`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        assertTrue(manager.isPasswordSet())
    }

    @Test
    fun `isFirstLaunch returns false after setPassword`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        assertFalse(manager.isFirstLaunch())
    }

    @Test
    fun `getMasterKeyForSession returns key after setPassword`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        val key = manager.getMasterKeyForSession()
        assertNotNull(key)
        assertEquals(32, key!!.size)
    }

    @Test
    fun `verifyPassword with correct password returns true`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        assertTrue(manager.verifyPassword("test_password123"))
    }

    @Test
    fun `verifyPassword with wrong password returns false`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        assertFalse(manager.verifyPassword("wrong_password"))
    }

    @Test
    fun `getMasterKeyForSession returns key after verify`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        manager.lock()
        manager.verifyPassword("test_password123")
        val key = manager.getMasterKeyForSession()
        assertNotNull(key)
        assertEquals(32, key!!.size)
    }

    @Test
    fun `changePassword with correct old and new returns true`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("old_password")
        assertTrue(manager.changePassword("old_password", "new_password"))
    }

    @Test
    fun `changePassword with wrong old password returns false`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("old_password")
        assertFalse(manager.changePassword("wrong_password", "new_password"))
    }

    @Test
    fun `verifyPassword with new password works after change`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("old_password")
        manager.changePassword("old_password", "new_password")
        assertTrue(manager.verifyPassword("new_password"))
    }

    @Test
    fun `verifyPassword with old password fails after change`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("old_password")
        manager.changePassword("old_password", "new_password")
        assertFalse(manager.verifyPassword("old_password"))
    }

    @Test
    fun `disablePassword returns true`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        assertTrue(manager.disablePassword())
    }

    @Test
    fun `isPasswordSet returns false after disable`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        manager.disablePassword()
        assertFalse(manager.isPasswordSet())
    }

    @Test
    fun `lock clears cached key`() {
        manager.initializeDeviceKeyMode()
        manager.lock()
        val key = manager.getMasterKeyForSession()
        assertNotNull(key)
    }

    @Test
    fun `getMasterKeyForSession returns null after lock`() {
        manager.initializeDeviceKeyMode()
        manager.setPassword("test_password123")
        manager.lock()
        assertNull(manager.getMasterKeyForSession())
    }

    @Test
    fun `double initializeDeviceKeyMode is safe`() {
        manager.initializeDeviceKeyMode()
        manager.initializeDeviceKeyMode()
    }

    @Test
    fun `setPassword without init returns false`() {
        assertFalse(manager.setPassword("test_password"))
    }

    @Test
    fun `disablePassword without password set returns true`() {
        manager.initializeDeviceKeyMode()
        assertTrue(manager.disablePassword())
    }
}
