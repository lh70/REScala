package replication

import com.github.ckuessner.aead.{Aead, AeadHelper, ByteArray}
import rdts.base.{Lattice, LocalUid}
import rdts.dotted.{Dotted, DottedLattice}
import rdts.time.Dots

type Secret = String

case class EncRDT[S](deltas: Set[Dotted[Secret]])

given encrdtLattice[S]: Lattice[EncRDT[S]] with
  def merge(left: EncRDT[S], right: EncRDT[S]): EncRDT[S] =
    val combined = left.deltas union right.deltas
    EncRDT(combined.filterNot(s => combined.exists(o => s.context <= o.context)))

extension [S](c: EncRDT[S])
  def version: Dots = c.deltas.map(_.context).reduceOption(Lattice.merge).getOrElse(Dots.empty)
  def send(data: Dotted[S], aead: Aead)(using
      rid: LocalUid
  )(using Conversion[S, ByteArray], Conversion[Dots, ByteArray]): EncRDT[S] =
    EncRDT(Set(Dotted(AeadHelper.toBase64(aead.encrypt(data.data.convert, data.context.convert).get), data.context)))

  def recombine(aead: Aead)(using
      DottedLattice[S],
      Conversion[Dots, ByteArray],
      Conversion[ByteArray, S]
  ): Option[Dotted[S]] =
    c.deltas.flatMap { ds =>
      aead
        .decrypt(AeadHelper.fromBase64(ds.data), ds.context.convert)
        .map(bytes => Dotted(bytes.convert: S, ds.context))
        .toOption
    }.reduceOption(Lattice.merge)
