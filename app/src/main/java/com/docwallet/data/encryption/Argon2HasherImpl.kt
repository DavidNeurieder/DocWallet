package com.docwallet.data.encryption

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

class Argon2HasherImpl : Argon2Hasher {
    override fun hash(
        password: ByteArray,
        salt: ByteArray,
        tCostInIterations: Int,
        mCostInKibibyte: Int,
        parallelism: Int,
        hashLengthInBytes: Int,
    ): ByteArray {
        val argon2Kt = Argon2Kt()
        val result = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = tCostInIterations,
            mCostInKibibyte = mCostInKibibyte,
            parallelism = parallelism,
            hashLengthInBytes = hashLengthInBytes,
        )
        return result.rawHashAsByteArray()
    }
}
