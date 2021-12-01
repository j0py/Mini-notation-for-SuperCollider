// NodeProxy wrapper, enabling Tidal (-like) syntax
//
// example: NP(~a).snd("bd bd hh sn").play(0.8).mon(0.5)
//
NP {
	var proxy, sound, notes, amps, struct;

	*new { |a_proxy| ^super.new.init(a_proxy); }

	init { |a_proxy|
		proxy = a_proxy;
    
    // create a silent proxy, where (noisy) things can be added on the slots
		proxy.ar(2);
		proxy.source_({ Silent.ar!2 });

		^this;
	}

	snd { |val| 
    sound = NPParser.new.parse(val).post; 
    if(struct.isNil, { struct = sound; });
    ^this;
  }

	snd_ { |val| struct = nil; ^this.snd(val); }

	num { |val| 
    notes = NPParser.new.parse(val).post; 
    if(struct.isNil, { struct = notes; });
    ^this;
  }

	num_ { |val| struct = nil; ^this.num(val); }

	amp { |val| 
    amps = NPParser.new.parse(val); 
    if(struct.isNil, { struct = amps; });
    ^this;
  }

	amp_ { |val| struct = nil; ^this.amp(val); }

	// plays the NodeProxy.
  // @param vol: volume for the monitor
	mon { |vol=0|
		proxy.play(vol: vol.asFloat.clip(0.0, 1.0));
		^this;
	}

  // adds a Pbind that will play on the NodeProxy private bus
  // @param amp: volume (0.0 - 1.0) for the Pbind
	play { |amp|

    // use an environment to count the cycles and hold all the data
		var envir = (
			sound: sound,
			notes: notes,
      amps: amps,
      struct: struct,
			cycle: -1,
		);

    // add Pbind to the NodeProxy
		proxy.put(
			10, // should be parameter?
			Penvir(envir, Pbind(

        // calc cycle number (and store in envir for Plazy's)
				// generate a new dur pattern for the cycle
				// there may be alternating steps, fast/slow steps
				\dur, Pn(Plazy({ |ev| 
            ~cycle = ~cycle + 1;
            Pseq(~struct.durs(~cycle));
        })),

				\amp, Pn(Plazy({ |ev|
          var amps = [ amp ];

					// amps will override
					if(~amps.notNil, { amps = ~amps.names(~cycle) });

					Pseq(amps).asFloat.clip(0.0, 1.0);
				})),

				\soundname, Pn(Plazy({ |ev| Pseq(~sound.names(~cycle)); })),

				\notename, Pn(Plazy({ |ev|
					var notes = ~sound.notes(~cycle);

					if(~notes.notNil, { notes = ~notes.names(~cycle); });

					Pseq(notes); // strings!
				})),

        // the "~" name denotes a Rest to be played
				\type, Pfunc({ |ev|
          case
          { ev.soundname == "~" } { \rest }
          { ev.notename == "~" } { \rest }
          { \note };
				}),

				\instrument, Pfunc({ |ev|
          var sample, sound, note;

          ev.bufnum = 0;
          ev.midinote = 0;
          note = ev.notename.asInteger;
          sound = ev.soundname.asSymbol;
          sample = NPSamples.samples.atFail(sound, nil);

          case
          { ev.type == \rest } { \default }

          { sample.notNil }
          { 
            ev.bufnum = sample.wrapAt(note).bufnum;
            \np_playbuf;
          }

          {
            if(note < 20, { note = note + 60 }); // degree
            ev.midinote = note;
            // if the synthdef has extra controls, they do not appear
            // in the proxymixer "ed" screen. why?
            // instead of a synthdef, we maybe should always use a function?
            sound;
          }
				}),

        // debugging
				\trace, Pfunc({|ev|
					~cycle.asString 
          + ev.dur.asString 
          + ev.soundname 
          + ev.notename.asString
          + ev.bufnum.asString;
				}),
			).trace(\trace))
		);

		^this;
	}
}
