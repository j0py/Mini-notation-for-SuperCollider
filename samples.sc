Samples {
  classvar <samples=nil;

  *load { |path="samples", type="wav"|

    var s = Server.default;

    samples = samples ? Dictionary.new;

    s.waitForBoot({
      (path.resolveRelative +/+ "*")
      .pathMatch.do({|sub|
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

  *at { |key| ^samples.at(key.asSymbol) }

  *atFail { |key, default| ^samples.atFail(key.asSymbol, default) }
}
