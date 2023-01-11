package vcoderocc

import freechips.rocketchip.tile.{TileKey, TileParams, RocketTileParams}
import freechips.rocketchip.subsystem.{RocketTilesKey, RocketSubsystem}
import freechips.rocketchip.config.Config

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
