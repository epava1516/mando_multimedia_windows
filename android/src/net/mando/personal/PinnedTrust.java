package net.mando.personal;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/** The PC's locally displayed code is the trust anchor; never accepts an unpinned certificate. */
final class PinnedTrust implements X509TrustManager {
    private final byte[] expected;
    PinnedTrust(byte[] expected) {
        if(expected==null || expected.length!=32)throw new IllegalArgumentException("SHA-256 requerido");
        this.expected=expected.clone();
    }
    public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
    public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException {
        throw new CertificateException("No client certificates");
    }
    public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException {
        try {
            if(chain==null || chain.length<1)throw new CertificateException("Certificado ausente");
            chain[0].checkValidity();
            byte[] actual=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
            if(!MessageDigest.isEqual(expected,actual))throw new CertificateException("El certificado no coincide con el código del PC");
        }catch(CertificateException error){throw error;}
        catch(Exception error){throw new CertificateException("No se pudo verificar el certificado",error);}
    }
}
