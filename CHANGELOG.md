# Change Log

## Versioning

This project tries to follows [Semantic Versioning](http://semver.org/),
although this is currently on a basis of educated guesses wrt binary compatibility.
Tools to support binary compatibility might be introduced in the future.


## [Unreleased][unreleased]
### Added
- `TypedActor.apply` to quickly create an actor from a total function
- Ask support for typed actors


## [1.2.0][1.2.0] - 2015-09-19
### Added
- typed `Props` gets all the pretty methods
- `Total` wrapper for usage with `typedBecome` or `typedReceive`
- `Untyped` wrapper for defining a `typedReceive` that can accept messages outside of the required type
- `PropsFor` constructors that can infer the message type from the given `TypedActor`
- `PropsOf` constructors that are type curried and can better infer the message type
- `untyped` and `typed` converters on typed and untyped actors, resp.
- lots of documentation

### Changed
- Rename `receiveMsg` to `typedBecome` and deprecate the former

### Removed
- `typedBecomeFull` in favor of `typedBecome` and `Total`
- Requirement of `TypedActor` to be a `case class`

### Fixed
- Sending a wrong message type the the untyped cast of an typed actor now results in an unhandled message instead of an error


## [1.1.0][1.1.0] - 2015-08-30
### Added
- `typedBecomeFull` on `TypedActor` for become with total functions

### Changed
- `typedBecome` and `receiveMsg` prefer partial functions over total ones


## [1.0.1][1.0.1] - 2015-08-30
### Added
- `typedBecome` on `TypedActor`

### Changed
- `TypedActor` wraps its `typedReceive` in a `LoggingReceive`

### Removed
- Support for Scala 2.10


## [1.0.0][1.0.0] - 2015-08-30
### Added
- Initial release, basic typed actors


[unreleased]: https://github.com/knutwalker/typed-actors/compare/v1.2.0...develop
[1.2.0]: https://github.com/knutwalker/typed-actors/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/knutwalker/typed-actors/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/knutwalker/typed-actors/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/knutwalker/typed-actors/compare/9cae71d329e808479e50cd6c10cd1ca4aca2343f...v1.0.0
