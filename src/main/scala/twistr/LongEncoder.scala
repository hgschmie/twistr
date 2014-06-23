package twistr

;

import kafka.utils.VerifiableProperties
import kafka.serializer.Encoder
import java.nio.ByteBuffer

class LongEncoder(props: VerifiableProperties) extends Encoder[Long] {

  override def toBytes(key: Long): Array[Byte] = {
    val buf = ByteBuffer.allocate(8);
    buf.putLong(key);
    return buf.array();
  }
}
