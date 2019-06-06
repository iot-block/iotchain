package jbok.benchmark

import jbok.crypto.bn256._
import org.openjdk.jmh.annotations.Benchmark

class BN256Benchmark extends JbokBenchmark {
  val fp2 = Fp2(BigInt("239846234862342323958623"), BigInt("2359862352529835623"))
  val fp12 = Fp12(
    Fp6(
      Fp2(BigInt("239846234862342323958623"), BigInt("2359862352529835623")),
      Fp2(BigInt("928836523"), BigInt("9856234")),
      Fp2(BigInt("235635286"), BigInt("5628392833"))
    ),
    Fp6(
      Fp2(BigInt("252936598265329856238956532167968"), BigInt("23596239865236954178968")),
      Fp2(BigInt("95421692834"), BigInt("236548")),
      Fp2(BigInt("924523"), BigInt("12954623"))
    )
  )

  val g1 = CurvePoint.curveGen * BigInt("21888242871839275222246405745257275088548364400416034343698204186575808495616")
  val g2 = TwistPoint.twistGen * BigInt("21888242871839275222246405745257275088548364400416034343698204186575808495616")

  @Benchmark
  def Fp12Square() = fp12.square()

  @Benchmark
  def Fp12SquareByMul() = fp12 * fp12

  @Benchmark
  def Fp2Add() = fp2 + fp2

  @Benchmark
  def Fp2AddByMul() = fp2 * 2

  @Benchmark
  def Fp2Square() = fp2.square()

  @Benchmark
  def Fp2SquareByMul() = fp2 * fp2

  @Benchmark
  def G1AddG1() = g1 + g1

  @Benchmark
  def G1MulBigInt() = g1 * BigInt("123456789")

  @Benchmark
  def pair() = BN256.pair(g1, g2)
}
