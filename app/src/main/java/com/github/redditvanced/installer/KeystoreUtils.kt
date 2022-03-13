/*
 * Copyright (c) 2021 Juby210 & Vendicated
 * Licensed under the Open Software License version 3.0
 */
package com.github.redditvanced.installer

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*


object KeystoreUtils {
    class KeySet(val publicKey: X509Certificate, val privateKey: PrivateKey)

    fun newKeystore(out: File) {
        val password = "password".toCharArray()
        val key = createKey()

        with(KeyStore.getInstance("BKS", "BC")) {
            load(null, password)
            setKeyEntry("alias", key.privateKey, password, arrayOf<Certificate>(key.publicKey))
            store(out.outputStream(), password)
        }
    }

    private fun createKey(): KeySet {
        val pair = KeyPairGenerator.getInstance("RSA").run {
            initialize(2048)
            generateKeyPair()
        }

        var serialNumber: BigInteger

        do serialNumber = SecureRandom().nextInt().toBigInteger()
        while (serialNumber < BigInteger.ZERO)

        val x500Name = X500Name("CN=RedditVanced Installer")
        val builder = X509v3CertificateBuilder(
            x500Name,
            serialNumber,
            Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L * 30L),
            Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 366L * 30L),
            Locale.ENGLISH,
            x500Name,
            SubjectPublicKeyInfo.getInstance(pair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA1withRSA").build(pair.private)

        return KeySet(
            JcaX509CertificateConverter().getCertificate(builder.build(signer)),
            pair.private
        )
    }

    fun loadKeyStore(file: File): KeySet {
        val keyStore = KeyStore.getInstance("BKS", "BC")
        file.inputStream().use { keyStore.load(it, null) }

        val password = "password".toCharArray()
        val alias = keyStore.aliases().nextElement()

        val certificate = keyStore.getCertificate(alias) as X509Certificate
        val privateKey = keyStore.getKey(alias, password) as PrivateKey

        return KeySet(certificate, privateKey)
    }
}