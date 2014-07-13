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

import com.google.inject.{AbstractModule, Inject, Module, Scopes}
import com.nesscomputing.config.{Config, ConfigProvider}
import com.nesscomputing.jackson.NessJacksonModule
import com.nesscomputing.logging.Log
import com.nesscomputing.server.StandaloneServer

object TwistrMain {
  val LOG = Log.findLog()

  def main(args: Array[String]) {
    val server = new TwistrMain()
    server.startServer()
  }
}

class TwistrMain extends StandaloneServer {

  @Inject val twistrConfig: TwistrConfig = null

  override def getServerType(): String = return "twistr"

  override def getMainModule(config: Config): Module = new AbstractModule() {
    override def configure() {
      install(new NessJacksonModule())
      bind(classOf[TwistrConfig]).toProvider(ConfigProvider.of(classOf[TwistrConfig])).in(Scopes.SINGLETON)
      bind(classOf[TwistrClient]).in(Scopes.SINGLETON)
    }
  }
}
