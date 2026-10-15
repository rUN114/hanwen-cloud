import java.nio.file.*;
import java.security.*;
import java.util.Base64;
public class GenerateKeys {
 public static void main(String[] args)throws Exception {
  Path dir=Path.of(args[0]);Files.createDirectories(dir);
  Path privatePath=dir.resolve("private.pem"),publicPath=dir.resolve("public.pem");
  if(Files.exists(privatePath)||Files.exists(publicPath))throw new IllegalStateException("Refusing to overwrite existing keys");
  KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(3072);KeyPair pair=g.generateKeyPair();
  write(privatePath,"PRIVATE KEY",pair.getPrivate().getEncoded());write(publicPath,"PUBLIC KEY",pair.getPublic().getEncoded());
 }
 static void write(Path p,String type,byte[] bytes)throws Exception{Files.writeString(p,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+type+"-----\n");}
}
