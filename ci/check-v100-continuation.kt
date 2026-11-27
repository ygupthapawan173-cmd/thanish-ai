import com.example.ai.ModelCooldownLedger
import com.example.ai.SmartModelPolicy
import com.example.autonomous.UpdateTransferPolicy
import com.example.data.*
fun main() {
 var n=0
 fun t(b:Boolean) { check(b) { "Continuation case ${n+1}" };n++ }
 val ledger=ModelCooldownLedger(2)
 ledger.mark("a","one",100,50)
 t(ledger.blocked("a","one",100));t(!ledger.blocked("b","one",100));t(!ledger.blocked("a","two",100))
 ledger.mark("a","one",110,2);t(ledger.blocked("a","one",149));t(!ledger.blocked("a","one",150))
 val full=ModelCooldownLedger(1);full.mark("a","x",10,50);full.mark("b","y",10,40)
 t(full.blocked("b","y",49));t(full.blocked("c","z",49));t(!full.blocked("c","z",50));t(full.blocked("a","x",59));t(!full.blocked("a","x",60))
 val extreme=ModelCooldownLedger();extreme.mark("a","x",Long.MAX_VALUE-10,100);t(extreme.blocked("a","x",Long.MAX_VALUE-1));t(!extreme.blocked("a","x",Long.MAX_VALUE));extreme.mark("a","bad",1,-1);t(!extreme.blocked("a","bad",1))
 val path="https://github.com/owner/repo/releases/download/v101/app.apk";val hash="a".repeat(64);val other="b".repeat(64)
 t(UpdateTransferPolicy.belongsTo(path,"owner/repo","v101","app.apk"));t(UpdateTransferPolicy.belongsTo(path,"OWNER/Repo","v101","app.apk"));t(!UpdateTransferPolicy.belongsTo(path,"owner/other","v101","app.apk"));t(!UpdateTransferPolicy.belongsTo(path,"owner/repo","v102","app.apk"));t(!UpdateTransferPolicy.belongsTo(path,"owner/repo","v101","other.apk"));
 for(part in listOf("%2F","%5c","%2e%2e","%252e")) t(!UpdateTransferPolicy.allowedAsset(path.replace("app.apk",part+"app.apk")))
 t(UpdateTransferPolicy.checksumFor(hash,"app.apk",true)==hash);t(UpdateTransferPolicy.checksumFor(hash,"app.apk",false)==null)
 t(UpdateTransferPolicy.checksumFor("$other  other.apk\n$hash  app.apk","app.apk",false)==hash)
 t(UpdateTransferPolicy.checksumFor("$hash  out/app.apk","app.apk",false)==hash)
 t(UpdateTransferPolicy.checksumFor("$hash *app.apk","app.apk",false)==hash)
 t(UpdateTransferPolicy.checksumFor("$hash  app.apk\n$other  app.apk","app.apk",false)==null)
 t(UpdateTransferPolicy.checksumFor("$hash  ../app.apk","app.apk",false)==null)
 t(UpdateTransferPolicy.checksumFor("x".repeat(65537),"app.apk",true)==null)
 for(id in listOf("", " bad", "bad\nmodel", "x".repeat(513)))t(!SmartModelPolicy.validId(id))
 t(SmartModelPolicy.validId("provider/model:free"))
 val q=ApiQuotaTracker;q.reset();q.recordSuccess("a",100,200,100,.1);q.recordFailure();q.recordSuccess("b",null,null,300)
 t(q.snapshot.value.averageLatencyMs==200L);t(q.snapshot.value.totalRequests==3);t(q.snapshot.value.failedRequests==1);t(q.snapshot.value.requestsWithoutCost==1)
 q.recordSuccess("x",Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Double.MAX_VALUE);q.recordSuccess("x",10,10,Long.MAX_VALUE,Double.MAX_VALUE)
 t(q.snapshot.value.reportedPromptTokens==Long.MAX_VALUE);t(q.snapshot.value.reportedOutputTokens==Long.MAX_VALUE);t(q.snapshot.value.knownCostUsd.isFinite());t(q.snapshot.value.averageLatencyMs>=0L)
 q.attach(object:ApiTelemetryStore{override fun load()=ApiTelemetrySnapshot(totalRequests=-7,failedRequests=100,reportedPromptTokens=-1,knownCostUsd=Double.NaN,averageLatencyMs=-50);override fun save(snapshot:ApiTelemetrySnapshot)=true})
 t(q.snapshot.value.totalRequests==0);t(q.snapshot.value.failedRequests==0);t(q.snapshot.value.reportedPromptTokens==0L);t(q.snapshot.value.knownCostUsd==0.0);t(q.snapshot.value.averageLatencyMs==0L)
 q.attach(object:ApiTelemetryStore{override fun load()=ApiTelemetrySnapshot(totalRequests=Int.MAX_VALUE,failedRequests=Int.MAX_VALUE);override fun save(snapshot:ApiTelemetrySnapshot)=true})
 q.recordFailure();q.recordSuccess("x",1,1,1);t(q.snapshot.value.totalRequests==Int.MAX_VALUE);t(q.snapshot.value.failedRequests==Int.MAX_VALUE)
 q.reset();println("$n/$n continuation policy checks passed (not feature count)")
}
