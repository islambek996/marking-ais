package kg.teksher.ais.code;
import com.google.zxing.*; import com.google.zxing.client.j2se.MatrixToImageWriter; import com.google.zxing.common.BitMatrix; import com.google.zxing.datamatrix.DataMatrixWriter; import org.springframework.stereotype.Service; import java.io.*; import java.util.*; import java.util.Base64;
@Service public class DataMatrixService{
 public String pngBase64(String payload){try{BitMatrix m=new DataMatrixWriter().encode(payload,BarcodeFormat.DATA_MATRIX,300,300,Map.of(EncodeHintType.MARGIN,2));ByteArrayOutputStream o=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(m,"PNG",o);return Base64.getEncoder().encodeToString(o.toByteArray());}catch(IOException e){throw new IllegalStateException("Не удалось создать DataMatrix",e);}}
}