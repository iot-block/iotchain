package jbok.core.messages

import jbok.core.models.BlockBody

case class BlockBodies(bodies: List[BlockBody]) extends Message
