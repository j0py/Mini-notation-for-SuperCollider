// NodeProxy wrapper, enabling Tidal (-like) syntax
//
// example: NP(~a).snd("bd bd hh sn").play(0.8).mon(0.5)
//
NP {
	var proxy, sound, notes, durmul=1;

	*new { |a_proxy| ^super.new.init(a_proxy); }

	init { |a_proxy|
		proxy = a_proxy;
    
    // create a silent proxy, where (noisy) things can be added on the slots
		proxy.ar(2);
		proxy.source_({ Silent.ar!2 });

		^this;
	}

	snd { |val| sound = NPParser.new.parse(val); ^this }

	num { |val| notes = NPParser.new.parse(val); ^this }

	beats { |val| durmul = val.asFloat; ^this }

	// plays the NodeProxy.
  // @param vol: volume for the monitor
  //
	mon { |vol=0|
		proxy.play(vol: vol.asFloat.clip(0.0, 1.0));
		^this;
	}

  // adds a Pbind that will play on the NodeProxy private bus
  // @param amp: volume (0.0 - 1.0) for the Pbind
  //
	play { |amp|

    // use an environment to count the cycles and hold all the data
		var envir = (
			sound: sound,
			notes: notes,
			durmul: durmul,
			cycle: -1,
		);

    // add Pbind to the NodeProxy
		proxy.put(
			10,
			Penvir(envir, Pbind(
				\amp, amp.asFloat.clip(0.0, 1.0),
				\group, NPSamples.groups[\src],

				// generate a new dur pattern per cycle
				// (there may be alternating steps)
				\dur, Pn(Plazy({ |ev|
					~cycle = ~cycle + 1;
					Pseq(~sound.durs(~cycle) * ~durmul, 1);
				})),

				\samplename, Pn(Plazy({ |ev|
					Pseq(~sound.names(~cycle));
				})),

				\samplenumber, Pn(Plazy({ |ev|
					var numbers = ~sound.numbers(~cycle);

					// notes will overrule numbers
					if(notes.notNil, {
						numbers = ~notes.names(~cycle);
					});

					Pseq(numbers).asInteger;
				})),

        // the "~" name denotes a Rest to be played
				\type, Pfunc({ |ev|
					if(ev.samplename == "~", \rest, \note);
				}),

				\instrument, Pfunc({ |ev|
					var sample = NPSamples.samples
					.at(ev.samplename.asSymbol);

					if(sample.notNil,
						\np_playbuf,
						ev.samplename.asSymbol
					);
				}),

				\midinote, Pfunc({ |ev|
					if(ev.samplenumber < 20, {
						ev.samplenumber + 60;
					}, ev.samplenumber);
				}),

				\buf, Pfunc({ |ev|
					var sample = NPSamples.samples
					.at(ev.samplename.asSymbol);

					if(sample.notNil, {
						sample.wrapAt(ev.samplenumber);
					}, 0);
				}),

        // debugging
				\trace, Pfunc({|ev|
					~cycle.asString 
          + ev.dur.asString 
          + ev.samplename 
          + ev.samplenumber.asString;
				}),
			).trace(\trace))
		);

		^this;
	}
}
