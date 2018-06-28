package jbok

import jbok.crypto.codec.CodecSyntax
import jbok.crypto.hashing.HashingSyntax

package object crypto extends HashingSyntax with CodecSyntax
