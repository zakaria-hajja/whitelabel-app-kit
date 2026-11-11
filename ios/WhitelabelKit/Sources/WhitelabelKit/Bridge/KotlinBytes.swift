import Foundation
import Shared

extension Data {
    /** These bytes as a Kotlin ByteArray (tokens are small; a copy is fine). */
    var kotlinByteArray: KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() { array.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        return array
    }

    init(kotlin array: KotlinByteArray) {
        self.init((0..<array.size).map { UInt8(bitPattern: array.get(index: $0)) })
    }
}
