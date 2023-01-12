package vcoderocc

import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.tile.{BuildRoCC, TileKey, TileParams, RocketTileParams, OpcodeSet}
import freechips.rocketchip.subsystem.{RocketTilesKey, RocketSubsystem}
import freechips.rocketchip.config.{Config, Field, Parameters}

/** Mixin to build a chip that includes a VCode accelerator.
  */
class WithVCodeAccel extends Config((site, here, up) => {
  case BuildRoCC => List (
    (p: Parameters) => {
      val vcodeAccel = LazyModule(new VCodeAccel(OpcodeSet.custom0)(p))
      vcodeAccel
    })
})

/** Design-level configuration option to toggle the synthesis of print statements
  * in the synthesized hardware design.
  */
case object VCodePrintfEnable extends Field[Boolean](false)

/** Mixin to enable print statements from the synthesized design.
  * This mixin should only be used AFTER the WithVCodeAccel mixin.
  */
class WithVCodePrintf extends Config((site, here, up) => {
  case VCodePrintfEnable => true
})


/** Adds a TileKey configuration, making the simplified testing design a part of
  * the TileLink network, allowing for the processor and accelerator to communicate
  * with the TileLink network.
  *
  * This is needed for the unit tests which require an implicit Parameters object
  * be passed to modules. */
class TileKeyTestConfig extends Config((site, here, up) => {
  case freechips.rocketchip.tile.TileKey => RocketTileParams(
    core = freechips.rocketchip.rocket.RocketCoreParams(), // Use the default Rocket Core configuration
    name = Some("VCode RoCC TileParams Config"),
  )
})

/** A simplified VCode accelerator Config-uration that allows for quick unit
  * tests. It reuses a default Rocket configuration, while adding TileLink
  * support required for RoCC tests. */
class VCodeTestConfig extends Config(
  new vcoderocc.WithVCodeAccel ++
  new TileKeyTestConfig ++
  new freechips.rocketchip.system.DefaultConfig)
