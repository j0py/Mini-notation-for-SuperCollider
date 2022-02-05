# Mini-notation-for-SuperCollider
Use Tidal Mini notation in a Pbind.

When exploring Tidal i liked the short "mini notation" syntax a lot.  
Wish we had that as an option in SuperCollider.. now, we do!

In Tidal you specify things like so: ```"bd:1 ~ sn:3 hh"```.

This string specifies what should happen during a "cycle".

In the example we see 4 steps in the cycle, separated by spaces.   
In step 1 sample number 1 from the "bd" samples is played.   
Step 2 is a Rest step.
Step 3 plays sample number 3 from the "sn" samples.  
Step 4 plays sample 0 from the "hh" samples.   

The colon is optional; it divides the step in a ```string:number```. You can omit the colon and the number, and in that case number 0 is assumed.

The part before the colon may also be a synthdef name, and then the number after the colon is interpreted as a note number (degree).

A mini-notation string like above can specify a lot for a Pbind:

When playing a sample:
- the \dur is specified: 4 steps each with duration 0.25 (1 cycle = 1 beat)
- the \instrument is assumed a \playbuf synthdef which needs a \bufnum.
- the \bufnum can be calculated from sample name and sample number.
- the \type is \note, but if "~" is encountered, the \type = \rest.

When playing a synthdef:
- the \dur is specified like with the sample above.
- the \instrument is the string part before the colon (a synthdef).
- the \degree is the part after the colon (an integer).
- the \type can be generated as with the sample above.

Tidal uses this notation multiple times on one line to specify multiple things. ```$ bd:1 ~ sn:2 hh # 3 0 1 2``` Specifies the same as above, only after the ```#``` a second mini-notated string is specified, which overrides the _number_ values of the ```$``` string before it.

## Samples

Samples are expected in ```Library.at(\samples)```.

If you have a "samples" folder in the same folder as your current .scd file, then you could do this:

```
s.waitForBoot({
	("samples".resolveRelative +/+ "*").pathMatch.do({ |bank|
		Library.put(
			\samples,
			bank.basename.withoutTrailingSlash.asSymbol,
			(bank +/+ "*.*").pathMatch.collect({ |file|
				Buffer.read(s, file)
			})
		);
	});
	s.sync;
});
```

This would read ALL files (.wav, .aiff, etc) as samples into the library.

Each sub-folder of the "samples" folder will appear in the Library under the \samples key, and it will hold an Array with audio Buffers read from the files that were found in the sub-folder.

## The idea

We could use mini-notation in a ProxySpace to specify keys for a Pbind like so:

```
~a.play;
~a[0] = Pbindf(
	Mbind("S bd:2 hh:1 cl:1 sn:7 N 1 2 3 A 6 2 P -9 9"),
	\amp, Mnum("0.25 <0.5 1> 0.75"),
	\pan, Pwhite(-1, 1),
);
```

The Mbind generates a Pbind, and the Mnum generates a Pattern.

### Mbind

The Mbind class returns a Pbind based on the given mini-notation string.  

In fact, you can give it 4 mini-notation strings: S is for the sound, N is for the number, A is for \amp, P is for \pan. All 4 are optional.

All 4 mini-notation strings are capable of delivering the \dur pattern for the Pbind, so which one should be used?

The _first_ specified string will deliver the \dur pattern, but it is overridden by the _last_ specified string which has an extra + sign added.

```Mbind("S bd:2 hh:1 cl:1 sn:7 N 1 2 3 A+ 6 2 2 P -9 9"),```

The \dur pattern generated by the S part is not used in the final Pbind, but instead the \dur pattern generated by the A part is used. This would give a triplet feel with accent on each first note.

Amp, by the way, is specified as an Integer number from 0 - 9. It is divided by 9 internally, so the range ultimately is 0.0 - 1.0. This way, the notation is shortened a bit.

Pan is specified as an Integer from -9 to 9, and also divided by 9, resulting in -1.0, 1.0 range. Having to write things like 0.33 or 0.45 is cumbursome, so just a simple 1-digit number, which will be divided by 9 will work faster. How many different panning positions do you need?

### Mnum

Mnum can generate a Pattern for you:

```
	\xxx, Mnum("0.25 <0.5 1> 0.75"),
```

Mnum will parse the mini-notation string, and only use the string parts before the colons. So when using Mnum, you always omit the colons and numbers, like above. Each string will we converted to a floating point number, and these values will be returned as a Pseq pattern.

In the example above you see two strange characters: ```< and >```.   
These are part of the mini-notation as defined for the Tidal language.   
I have not incorporated all mini-notation options, but a useable bunch of them.

## Mini-notation options

### Nesting, alternating

Nesting (```[step1 step2 ..]```) puts one or more steps in the duration space of one step. Samples/notes are thus played faster.

Alternating (```<step1 step2 ..>```) will alternate the steps between the brackets. The first step is played during the first cycle, and during the next cycle, the second step will be played (if any) and so on.

You may nest these things as deep as you want.

### Speed

```*``` ```"bd sn*1.5"``` plays 1.5 snare steps inside 1 step.

This is a bit tricky: during the step with duration 0.5, 1.5 snare-steps are played.   
That means, in cycle 0, a snare is played at the beginning of the 0.5 duration space, and another is played at 2/3 of the 0.5 beat duration.
In cycle 1, the 0.5 beat duration will start with silence from the snare step from cycle 0, and on 1/3 of the 0.5 beat duration, a snare will be played. This last snare will exactly fill up the cycle. Cycle 2 will be the same as cycle 0, cycle 3 the same as cycle 1, etc.   
The result is, that the snaredrum plays 1.5 times faster "through" the rhythm.

```/``` ```"bd sn/1.5"``` like above, but plays snare 1.5 times slower.

```@``` ```[bd sn@0.5]``` makes duration of the snare half as long

You may also speed up nested groups of course!

```"bd [hh <hh cl> hh]/2 <~ sn> hh"```

## Installation

Use ```git clone``` to get the sources somewhere on disk (just 1 file).

Create a symbolic link inside the ```~/.local/share/SuperCollider/Extensions``` folder, pointing to the place where you have cloned this repository to.

Like this: ```ln -s /somewhere/on/disk/cloned-repository mini```.

In Extensions folder, you now have a symbolic link named "mini" pointing to the contents of the repository.

Start SuperCollider (this will recompile all classes) and the Mbind / Mnum classes should be available. SuperCollider will follow the symbolic link and discover the source file(s) of the extensions.

The name of the symbolic link ("mini") may be anything.

This is how you can install it on Linux. Without using symbolic links, you can of course just copy ```mini.sc``` into Extensions folder directly. But using a clones Github repository makes updating easier (or collaborating!).

## Future plans/ideas

The mini-notation parser itself could use some more capabilities:

```,``` paralell running steps

```!``` repeat the last step, e.g.: ```"bd bd bd"``` = ```"bd bd!"``` = ```"bd!3"```

```bd(3,8)``` euclidian rhythms ( ```bd:2(3,8)``` is also possible )

The string for Mbind splits into parts by using uppercase letters.   
Each uppercase letter indicates what part immediately following it means.

* (S)ound
* (N)umber
* (A)mp
* (P)an

This makes it easy to add more letters / features.

## I like Chucklib too

The ddwChucklib quark by James Harkins has a notation system that is very short too, and is also inspired by Tidal (thinking in "cycles").
In his notation, each character stands for 1 step of the cycle.

So specifying a cycle of 4 steps only takes 4 characters!

There are 2 special characters in his notation: the _space_ character and the "x".   
A space does not play anything, but it just takes up the time of 1 step. Any note played before the space will continue to sound, so in fact the space adds to the duration of the previously played note.  
The "x" character plays a rest, and the note played right before it will end when that rest is played.

Consider this 4 step sequence, using a long sounding Crash cymbal sample.

In Chucklib notation you can do this:

```"S cr N 1 2 "``` Crash sample 1 is hit at step 1, it continues to sound during step 2, and in step 3 Crash sample 2 is started, which continues to sound through step 4.

In Tidal notation, you would have to do this:

```"S cr N 1 2 "``` The cycle has only 2 steps instead of 4.

In Chucklib notation you can choke the Crash in step 2 by playing an "x":

```"S cr N 1x2 "``` Crash sample 1 starts in step 1, but is stopped in step 2, and in step 3 Crash sample 2 starts as before.

In Tidal notation, you would then have to do (something like) this:

```"S cr N [1 ~] 2"``` To choke the Crash, we have to add a rest in step 2. But then the cycle would have 3 steps, which gives a triplet feel, and we do not want that. So we have to next the first step and the added rest, so together they will take up half of the cycle. The third step (with value 2) will then get the other half of the cycle.

The Chucklib notation seems simpeler.

Would _nesting_ and _alternating_ be possible using the Chucklib notation?

Tidal: ```"S hh N 1 [1 ~ ~ 2] 1 4"``` End step 2 with an extra 16th hh hit.

Chucklib: ```"S hh N 1|1x 2|1|4"``` Using "|" to mark the steps.

Tidal: ```"S hh N 1 [1 <7 2>] 1 4"``` Double step 2 with an extra hh. Alternate the extra hh using sample 7 and 2.

Chucklib: ```"S hh N 1|1<72>|1|4"``` Perfectly possible if you consider ```<72>``` to take up the space of 1 step. Inside the ```<>``` there are 2 steps.

Let's try a little more complicated case:  

The second step should get an extra 16th hh at the end, using sample number 2 during even cycles, but during odd cycles, two 32nd hh strokes must be used using sample numbers 8 and 9.

Tidal: ```"S hh N 1 [1 ~ ~ <2 [8 9]>] 1 4"```

Chucklib: ```"S hh N 1|1x <2|89>|1|4"```

In Chucklib it is easy to stop the sample that started in step 1 using the "x". You can stop it in step 2, or step 3, or let it sound until step 4 starts.
In Tidal notation i would have to think harder to accomplish equal results.

In Tidal you can specify more than 1 sample / synthdef on one line.  
Using Chucklib notation this is only possible when using the "|" character to separate the steps (```"S bd|hh|sn N 1233"```).

In Chucklib, the "~" is used to specify a glissando. This can be used to glide from one pitch to another, but it could also glide any other Pbind parameter from one value to another (\amp, \pan, maybe even \dur).

```"S sn N 1111 A 0~9"``` During one cycle, we hear 4 snaredrum hits, with amplitudes 0, 0.25, 0.5, 0.75. Amplitude 1 is reached at the _end_ of step 4, because the A pattern uses a cycle containing only 1 step.  
The amp numbers given are divided by 9 internally so we can supply short integers from 0 to 9 in the notation instead of 0.25 and such.

Note that each uppercase letter in the notation must have a space character before and after it, to keep things readable / parseable. We will not put a space at the start of the notation string though.   
If a pattern ends with a space, then the next uppercase letter will have _two_ spaces before it.

I will make a new class called "Pmini" that supports the Chucklib notation, including the _alternate_ option with ```<>```. Nesting using ```[]``` does not seem necessary to have (yet).

Panning wil have to change to 0..8, divided by 4 and then subtract 1.  
This will then occupy 1 character and be able to pan in the center.

Might as well use 0..8 for amplitude as well to avoid confusion.

If the notation string has uppercase letters, then it will generate a Pbind.  
The _first_ part will supply ```\dur```, but this is overruled by the _last_ part where the uppercase letter has a "+" sign added to it (if any).
It is fun to switch this "+" from one part to the other while playing, especially if the parts chop one cycle in different numbers of steps.

If the notation does not contain uppercase letters then it will generate a Pseq returning float values.

```
~a.play;
~a[0] = Pbindf(
  Pmini("S bd N 12 1 A 8<13>3 P 0~8"),
  \anything_else, Pmini("1234"),
  );
```

This would be a 4 steps bass drum rhythm using samples 1 and 2, with a triplet feel accent pattern, and gliding pan. Wouldn't that be neat?

If you work with a synthdef, then the N part would give you degrees, and you would supply \octave and \scale as separate keys in the Pbindf. I can imagine maybe some extra parameters for Pmini to make that a bit shorter to specify.

Also Chucklib has nice additions like ```'``` and ```,``` to move a step up/down one octave, and also characters for sharps and flats. I put those in too.
