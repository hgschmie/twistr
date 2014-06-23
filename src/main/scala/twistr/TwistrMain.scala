package twistr

;

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.Module
import com.google.inject.Scopes
import com.nesscomputing.config.Config
import com.nesscomputing.config.ConfigProvider
import com.nesscomputing.jackson.NessJacksonModule
import com.nesscomputing.logging.Log
import com.nesscomputing.server.StandaloneServer

object TwistrMain {
  val LOG = Log.findLog()

  def main(args: Array[String]) {
    val server = new TwistrMain();
    server.startServer();
  }
}

class TwistrMain extends StandaloneServer {

  @Inject val twistrConfig: TwistrConfig = null

  override def getServerType(): String = return "twistr";

  override def getMainModule(config: Config): Module = new AbstractModule() {
    override def configure() {
      install(new NessJacksonModule())
      bind(classOf[TwistrConfig]).toProvider(ConfigProvider.of(classOf[TwistrConfig])).in(Scopes.SINGLETON)
      bind(classOf[TwistrClient]).in(Scopes.SINGLETON)
    }
  }
}
