package okhttp3
import java.io.Closeable
import java.io.IOException
interface Call { fun cancel(); fun enqueue(callback: Callback) }
interface Callback { fun onFailure(call: Call, e: IOException); fun onResponse(call: Call, response: Response) }
class Response : Closeable { var closed=false; override fun close(){closed=true} }
