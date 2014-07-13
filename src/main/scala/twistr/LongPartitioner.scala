/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package twistr

import kafka.producer.Partitioner
import kafka.utils.VerifiableProperties

class LongPartitioner(props: VerifiableProperties = null) extends Partitioner {

  override def partition(value: Any, numPartitions: Int): Int = {
    value match {
      case key: Long =>
        return (key % numPartitions).asInstanceOf[Int]
      case _ =>
        return 0
    }
  }
}
