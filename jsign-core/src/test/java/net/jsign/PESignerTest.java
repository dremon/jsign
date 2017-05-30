/**
 * Copyright 2012 Emmanuel Bourg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.jsign;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.Security;
import java.util.HashSet;
import java.util.List;

import junit.framework.TestCase;
import net.jsign.pe.PEFile;
import net.jsign.timestamp.AuthenticodeTimestamper;
import net.jsign.timestamp.TimestampingException;
import net.jsign.timestamp.TimestampingMode;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tsp.TSPAlgorithms;

public class PESignerTest extends TestCase {

    private static String PRIVATE_KEY_PASSWORD = "password";
    private static String ALIAS = "test";

    private KeyStore getKeyStore() throws Exception {
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream("target/test-classes/keystore.jks"), "password".toCharArray());
        return keystore;
    }

    public void testSign() throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-signed.exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD)
                .withTimestamping(false)
                .withProgramName("WinEyes")
                .withProgramURL("http://www.steelblue.com/WinEyes");
        
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals(1, signatures.size());
        
        CMSSignedData signature = signatures.get(0);
        
        assertNotNull(signature);
        
        peFile.printInfo(System.out);
    }

    public void testTimestampAuthenticode() throws Exception {
        testTimestamp(TimestampingMode.AUTHENTICODE, DigestAlgorithm.SHA1);
    }

    public void testTimestampRFC3161() throws Exception {
        testTimestamp(TimestampingMode.RFC3161, DigestAlgorithm.SHA256);
    }

    public void testTimestamp(TimestampingMode mode, DigestAlgorithm alg) throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-timestamped-" + mode.name().toLowerCase() + ".exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD);
        signer.withDigestAlgorithm(alg);
        signer.withTimestamping(true);
        signer.withTimestampingMode(mode);
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull("list of signatures null", signatures);
        assertEquals("number of signatures", 1, signatures.size());
        
        assertNotNull("null signature", signatures.get(0));
        SignatureAssert.assertTimestamped("Invalid timestamp", signatures.get(0));
        
        peFile.printInfo(System.out);
    }

    /**
     * Tests that a custom Timestamper implementation can be provided.
     */
    public void testWithTimestamper() throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-timestamped-custom.exe");

        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);

        final HashSet<Boolean> called = new HashSet<>();
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD);
        signer.withDigestAlgorithm(DigestAlgorithm.SHA1);
        signer.withTimestamping(true);
        signer.withTimestamper(new AuthenticodeTimestamper() {
            
            @Override
            protected CMSSignedData timestamp(DigestAlgorithm algo, byte[] encryptedDigest) throws IOException, TimestampingException {
                called.add(true);
                return super.timestamp(algo, encryptedDigest);
            }

        });
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals(1, signatures.size());
        
        CMSSignedData signature = signatures.get(0);
        
        assertNotNull(signature);
        
        assertTrue("expecting our Timestamper to be used", called.contains(true));
        
        SignatureAssert.assertTimestamped("Invalid timestamp", signature);
    }

    public void testSignTwice() throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-signed-twice.exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD)
                .withDigestAlgorithm(DigestAlgorithm.SHA1)
                .withTimestamping(true)
                .withProgramName("WinEyes")
                .withProgramURL("http://www.steelblue.com/WinEyes");
        
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 1, signatures.size());
        
        assertNotNull(signatures.get(0));
        SignatureAssert.assertTimestamped("Invalid timestamp", signatures.get(0));
        
        // second signature
        signer.withDigestAlgorithm(DigestAlgorithm.SHA256);
        signer.withTimestamping(false);
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 2, signatures.size());
        
        assertNotNull(signatures.get(0));
        SignatureAssert.assertTimestamped("Timestamp corrupted after adding the second signature", signatures.get(0));
    }

    public void testSignThreeTimes() throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-signed-three-times.exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD)
                .withDigestAlgorithm(DigestAlgorithm.SHA1)
                .withTimestamping(true)
                .withProgramName("WinEyes")
                .withProgramURL("http://www.steelblue.com/WinEyes");
        
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 1, signatures.size());
        
        assertNotNull(signatures.get(0));
        SignatureAssert.assertTimestamped("Invalid timestamp", signatures.get(0));
        
        // second signature
        signer.withDigestAlgorithm(DigestAlgorithm.SHA256);
        signer.withTimestamping(false);
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 2, signatures.size());
        
        assertNotNull(signatures.get(0));
        SignatureAssert.assertTimestamped("Timestamp corrupted after adding the second signature", signatures.get(0));
        
        // third signature
        signer.withDigestAlgorithm(DigestAlgorithm.SHA512);
        signer.withTimestamping(false);
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 3, signatures.size());
        
        assertNotNull(signatures.get(0));
        SignatureAssert.assertTimestamped("Timestamp corrupted after adding the third signature", signatures.get(0));
    }

    public void testReplaceSignature() throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-re-signed.exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD)
                .withDigestAlgorithm(DigestAlgorithm.SHA1)
                .withProgramName("WinEyes")
                .withProgramURL("http://www.steelblue.com/WinEyes");
        
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 1, signatures.size());
        
        assertNotNull(signatures.get(0));
        
        // second signature
        signer.withDigestAlgorithm(DigestAlgorithm.SHA256);
        signer.withTimestamping(false);
        signer.withSignaturesReplaced(true);
        signer.sign(peFile);
        
        peFile = new PEFile(targetFile);
        signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 1, signatures.size());
        
        assertNotNull(signatures.get(0));
        
        assertEquals("Digest algorithm", DigestAlgorithm.SHA256.oid, signatures.get(0).getDigestAlgorithmIDs().iterator().next().getAlgorithm());
    }

    public void testInvalidAuthenticodeTimestampingAutority() throws Exception {
        testInvalidTimestampingAutority(TimestampingMode.AUTHENTICODE);
    }

    public void testInvalidRFC3161TimestampingAutority() throws Exception {
        testInvalidTimestampingAutority(TimestampingMode.RFC3161);
    }

    public void testInvalidTimestampingAutority(TimestampingMode mode) throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-timestamped-unavailable-" + mode.name().toLowerCase() + ".exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD);
        signer.withDigestAlgorithm(DigestAlgorithm.SHA1);
        signer.withTimestamping(true);
        signer.withTimestampingMode(mode);
        signer.withTimestampingAutority("http://www.google.com/" + mode.name().toLowerCase());
        signer.withTimestampingRetries(1);
        
        try {
            signer.sign(peFile);
            fail("IOException not thrown");
        } catch (TimestampingException e) {
            assertTrue("Missing suppressed IOException", e.getSuppressed() != null && e.getSuppressed().length > 0 && e.getSuppressed()[0].getClass().equals(IOException.class));
            // expected
        }

        peFile = new PEFile(targetFile);
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertTrue(signatures.isEmpty());
    }

    public void testBrokenAuthenticodeTimestampingAutority() throws Exception {
        testBrokenTimestampingAutority(TimestampingMode.AUTHENTICODE);
    }

    public void testBrokenRFC3161TimestampingAutority() throws Exception {
        testBrokenTimestampingAutority(TimestampingMode.RFC3161);
    }

    public void testBrokenTimestampingAutority(TimestampingMode mode) throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-timestamped-broken-" + mode.name().toLowerCase() + ".exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD);
        signer.withDigestAlgorithm(DigestAlgorithm.SHA1);
        signer.withTimestamping(true);
        signer.withTimestampingMode(mode);
        signer.withTimestampingAutority("http://github.com");
        signer.withTimestampingRetries(1);
        
        try {
            signer.sign(peFile);
            fail("TimestampingException not thrown");
        } catch (TimestampingException e) {
            // expected
        }

        peFile = new PEFile(targetFile);
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertTrue(signatures.isEmpty());
    }

    public void testInvalidTimestampingURL() throws Exception {
        PEFile peFile = new PEFile(new File("target/test-classes/wineyes.exe"));
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD);
        signer.withDigestAlgorithm(DigestAlgorithm.SHA1);
        signer.withTimestamping(true);
        signer.withTimestampingMode(TimestampingMode.RFC3161);
        signer.withTimestampingAutority("example://example.com");
        signer.withTimestampingRetries(1);
        
        try {
            signer.sign(peFile);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testAuthenticodeTimestampingFailover() throws Exception {
        testTimestampingFailover(TimestampingMode.AUTHENTICODE, "http://timestamp.comodoca.com/authenticode");
    }

    public void testRFS3161TimestampingFailover() throws Exception {
        testTimestampingFailover(TimestampingMode.RFC3161, "http://timestamp.comodoca.com/rfc3161");
    }

    public void testTimestampingFailover(TimestampingMode mode, String validURL) throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-timestamped-failover-" + mode.name().toLowerCase() + ".exe");
        
        FileUtils.copyFile(sourceFile, targetFile);
        
        PEFile peFile = new PEFile(targetFile);
        
        PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD);
        signer.withDigestAlgorithm(DigestAlgorithm.SHA1);
        signer.withTimestamping(true);
        signer.withTimestampingMode(mode);
        signer.withTimestampingRetryWait(1);
        signer.withTimestampingAutority("http://www.google.com/" + mode.name().toLowerCase(), "http://github.com", validURL);

        signer.sign(peFile);

        peFile = new PEFile(targetFile);
        List<CMSSignedData> signatures = peFile.getSignatures();
        assertNotNull(signatures);
        assertEquals("number of signatures", 1, signatures.size());
        
        assertNotNull(signatures.get(0));
        SignatureAssert.assertTimestamped("Invalid timestamp", signatures.get(0));
    }

    /**
     * Tests that it is possible to specify a signature algorithm.
     *
     * @throws Exception
     */
    public void testWithSignatureAlgorithmSHA1withRSA() throws Exception {
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-signed.exe");

        FileUtils.copyFile(sourceFile, targetFile);

        PEFile peFile = null;
        try {
            peFile = new PEFile(targetFile);

            PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD)
                    .withTimestamping(false)
                    .withDigestAlgorithm(DigestAlgorithm.SHA256)
                    .withSignatureAlgorithm("SHA1withRSA");

            signer.sign(peFile);

            peFile = new PEFile(targetFile);
            List<CMSSignedData> signatures = peFile.getSignatures();
            assertNotNull(signatures);
            assertEquals(1, signatures.size());

            CMSSignedData signedData = signatures.get(0);
            assertNotNull(signedData);

            // Check the signature algorithm
            SignerInformation si = signedData.getSignerInfos().getSigners().iterator().next();
            assertEquals("Digest algorithm", TSPAlgorithms.SHA1, si.getDigestAlgorithmID().getAlgorithm());
            assertEquals("Encryption algorithm", PKCSObjectIdentifiers.rsaEncryption.getId(), si.getEncryptionAlgOID());
        } finally {
            if (peFile != null) {
                peFile.close();
            }
        }
    }

    /**
     * Tests that it is possible to specify a signature algorithm who's name is
     * not simply a concatenation of a digest algorithm and the key algorithm.
     *
     * This test also sets the signature provider as a provider supporting
     * the RSASSA-PSS algorithms might not be installed.
     *
     * @throws Exception
     */
    public void testWithSignatureAlgorithmSHA256withRSAandMGF1() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        
        File sourceFile = new File("target/test-classes/wineyes.exe");
        File targetFile = new File("target/test-classes/wineyes-signed.exe");

        FileUtils.copyFile(sourceFile, targetFile);

        PEFile peFile = null;
        try {
            peFile = new PEFile(targetFile);

            PESigner signer = new PESigner(getKeyStore(), ALIAS, PRIVATE_KEY_PASSWORD)
                    .withTimestamping(false)
                    .withDigestAlgorithm(DigestAlgorithm.SHA1)
                    .withSignatureAlgorithm("SHA256withRSAandMGF1", "BC");

            signer.sign(peFile);

            peFile = new PEFile(targetFile);
            List<CMSSignedData> signatures = peFile.getSignatures();
            assertNotNull(signatures);
            assertEquals(1, signatures.size());

            CMSSignedData signedData = signatures.get(0);
            assertNotNull(signedData);

            // Check the signature algorithm
            SignerInformation si = signedData.getSignerInfos().getSigners().iterator().next();
            assertEquals("Digest algorithm", NISTObjectIdentifiers.id_sha256, si.getDigestAlgorithmID().getAlgorithm());
            assertEquals("Encryption algorithm", PKCSObjectIdentifiers.id_RSASSA_PSS.getId(), si.getEncryptionAlgOID());
        } finally {
            if (peFile != null) {
                peFile.close();
            }
        }
    }
}
