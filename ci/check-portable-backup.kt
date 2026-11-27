import com.example.security.PortableBackupCipher as B
fun main() {
    var n=0
    fun verify(ok:Boolean) {check(ok); n++}
    val p="Test-only passphrase 2026".toCharArray()
    val data="తెలుగు • private memory".toByteArray()
    val a=B.seal(data,p); val b=B.seal(data,p)
    verify(B.open(a,p).contentEquals(data))
    verify(!a.contentEquals(b))
    verify(B.open(b,p).contentEquals(data))
    verify(runCatching { B.open(a,"Another test passphrase".toCharArray()) }.isFailure)
    verify(runCatching { B.open(a.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()},p) }.isFailure)
    verify(runCatching { B.open(a.copyOf().also {it[8]=(it[8].toInt() xor 1).toByte()},p) }.isFailure)
    verify(runCatching { B.open(a.copyOf(a.size-1),p) }.isFailure)
    verify(runCatching { B.open(a.copyOf().also {it[0]=0},p) }.isFailure)
    verify(runCatching { B.seal(data,"short".toCharArray()) }.isFailure)
    verify(runCatching { B.seal(byteArrayOf(),p) }.isFailure)
    verify(runCatching { B.seal(ByteArray(B.MAX_PLAINTEXT+1),p) }.isFailure)
    verify(a.size==data.size+B.HEADER_SIZE+16)
    p.fill('\u0000'); println("Portable backup crypto checks: $n/$n passed")
}
