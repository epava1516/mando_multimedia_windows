package net.mando.personal;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/** Use a generated local PEM certificate; never needs a production key or server. */
public final class PinnedTrustTest {
    public static void main(String[] args)throws Exception {
        X509Certificate certificate;
        try(FileInputStream input=new FileInputStream(args[0])) {
            certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        PinnedTrust accepted=new PinnedTrust(digest);
        accepted.checkServerTrusted(new X509Certificate[]{certificate},"ECDHE_ECDSA");
        digest[0]^=1; // The trust anchor must not be mutable by its caller.
        accepted.checkServerTrusted(new X509Certificate[]{certificate},"ECDHE_ECDSA");
        try {new PinnedTrust(digest).checkServerTrusted(new X509Certificate[]{certificate},"ECDHE_ECDSA");throw new AssertionError("Accepted wrong pin");}
        catch(CertificateException expected){ }
        try {accepted.checkServerTrusted(new X509Certificate[0],"ECDHE_ECDSA");throw new AssertionError("Accepted no certificate");}
        catch(CertificateException expected){ }
        try {accepted.checkClientTrusted(new X509Certificate[]{certificate},"EC");throw new AssertionError("Accepted client certificate");}
        catch(CertificateException expected){ }
        System.out.println("5 certificate pinning checks passed");
    }
}
