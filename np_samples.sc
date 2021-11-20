// load samples, creates groups, defines a synthdef
NPSamples {
	classvar <groups, <samples=nil;

	*load {
		var s = Server.default;

		if(samples.notNil, { ^this; });

		s.waitForBoot({

			samples = Dictionary.new;

			("samples".resolveRelative +/+ "*")
			.pathMatch.do({|sub|
				samples.put(
					sub.basename
					.withoutTrailingSlash.asSymbol,
					(sub +/+ "*.wav")
					.pathMatch.collect({|wav|
						Buffer.read(s, wav)
					})
				);
			});

			s.sync;

			groups = Dictionary.newFrom([
				\mod, Group.tail,
				\src, Group.tail,
				\fx1, Group.tail,
				\fx2, Group.tail,
			]);

			SynthDef(\np_playbuf, {
				arg out=0, bufnum, amp=1, pan=0, spread=1;
				var sig;
				sig = PlayBuf.ar(2, bufnum, BufRateScale.kr(bufnum), doneAction: 2);
				sig = Splay.ar(sig, spread, amp, pan);
				Out.ar(out, sig);
			}).add;

			s.sync;

			("Samples" + samples.keys).postln;
		});
	}
}
