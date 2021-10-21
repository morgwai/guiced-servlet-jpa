# Sample apps for guiced-servlet-jpa library: multiple persistence units version

This is almost exactly the same app as [the one in ../sample/ folder](../sample). The only difference is that this version stores QueryRecords and ChatLogs into to separate persistence units.
Run `diff -U 6 -r --color sample/src/ sample-multi-jpa/src/` in the parent folder to see the exact differences.

if you want to switch to `jakarta` flavor, run `./jakarta.sh` in this folder as well as in [../sample](../sample) (source files that would be identical between these 2 apps are symbolic links).
