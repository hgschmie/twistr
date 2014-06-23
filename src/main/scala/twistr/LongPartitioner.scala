package twistr

;

import kafka.utils.VerifiableProperties
import kafka.producer.Partitioner

class LongPartitioner(props: VerifiableProperties = null) extends Partitioner {

  override def partition(value: Any, numPartitions: Int): Int = {
    value match {
      case key: Long =>
        return (key % numPartitions).asInstanceOf[Int];
      case _ =>
        return 0;
    }
  }
}
