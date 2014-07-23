package org.msgpack.value

import org.msgpack.core.{MessagePack, MessageUnpacker, MessagePackSpec}
import ValueFactory._
import scala.util.Random
import org.msgpack.value.holder.IntegerHolder

/**
 * Created on 6/13/14.
 */
class CursorTest extends MessagePackSpec {

  def sampleData = createMessagePackData { packer =>
    packer.pack(
      ValueFactory.newArray(
        newInt(10),
        newBinary("message pack".getBytes(MessagePack.UTF8)),
        newString("hello")
      )
    )
  }

  def intSeq(n:Int) = createMessagePackData { packer =>
    (0 until n).foreach { i =>
      packer.packInt(Random.nextInt(65536))
    }
  }
  def binSeq(n:Int) = createMessagePackData { packer =>
    (0 until n).foreach { i =>
      val len = Random.nextInt(256)
      val b = new Array[Byte](len)
      Random.nextBytes(b)
      packer.packBinaryHeader(b.length).writePayload(b)
    }
  }


  "Cursor" should {

    "have array cursor" taggedAs("array") in {

      val cursor = new MessageUnpacker(sampleData).getCursor
      // Traverse as references
      val arrCursor = cursor.nextRef().getArrayCursor
      arrCursor.size() shouldBe 3

      import scala.collection.JavaConversions._
      for(v <- arrCursor) {
        info(s"[${v.getValueType}]\t${v}")
      }
    }

    "traverse ValueRef faster than traversing Value" taggedAs("ref") in {
      val N = 10000
      val data = binSeq(N)

      time("traversal", repeat=100) {
        block("value") {
          val cursor = new MessageUnpacker(data).getCursor
          while(cursor.hasNext) {
            cursor.next()
          }
          cursor.close()
        }
        block("value-ref") {
          val cursor = new MessageUnpacker(data).getCursor
          while(cursor.hasNext) {
            cursor.nextRef()
          }
          cursor.close()
        }
      }

    }

    "have negligible overhead" taggedAs("perf") in {
      val N = 10000
      val data = intSeq(N)
      time("scan int-seq", repeat=1000) {
        block("unpacker") {
          val unpacker = new MessageUnpacker(data)
          val intHolder = new IntegerHolder()
          var count = 0
          while(unpacker.hasNext) {
            val vt = unpacker.getNextFormat.getValueType
            if(vt.isIntegerType) {
              unpacker.unpackInteger(intHolder);
              count += 1
            }
            else {
              throw new IllegalStateException(s"invalid format: ${vt}")
            }
          }
          unpacker.close()
          count shouldBe N
        }
        block("cursor") {
          var count = 0
          val cursor = new MessageUnpacker(data).getCursor
          while(cursor.hasNext) {
            val ref = cursor.nextRef()
            val v = ref.asInteger().toInt
            count += 1
          }
          cursor.close()
          count shouldBe N
        }
      }



    }


  }
}