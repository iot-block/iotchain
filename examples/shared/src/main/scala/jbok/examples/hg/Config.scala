package jbok.examples.hg

case class Config(
    n: Int, // the number of members in the population
    c: Int = 10, // frequency of coin rounds (such as c = 10)
    d: Int = 1 // rounds delayed before start of election (such as d = 1)
)
