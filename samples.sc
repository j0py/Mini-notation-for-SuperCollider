Samples {
  classvar <samples=nil;

  *load { |path="samples", type="wav"|

    var s = Server.default;

    if(samples.notNil, { ^this; });

    s.waitForBoot({
      var base;

      samples = Dictionary.new;

      base = PathName(this.class.filenameSymbol.asString).pathOnly;
      path = base ++ path.asString +/+ "*";
      path.pathMatch.do({|sub|
        samples.put(
          sub.basename
          .withoutTrailingSlash.asSymbol,
          (sub +/+ "*." ++ type)
          .pathMatch.collect({|file|
            Buffer.read(s, file)
          })
        );
      });

      s.sync;

      ("Samples" + samples.keys).postln;
    });
  }
}
