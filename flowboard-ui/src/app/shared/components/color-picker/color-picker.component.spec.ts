import { TestBed } from '@angular/core/testing';

import { ColorPickerComponent } from './color-picker.component';

describe('ColorPickerComponent', () => {
  it('selects and clears colors', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [ColorPickerComponent]
    }).createComponent(ColorPickerComponent);
    const component = fixture.componentInstance;
    spyOn(component.colorChange, 'emit');

    component.selectColor('#000000');
    expect(component.selectedColor).toBe('#000000');
    expect(component.colorChange.emit).toHaveBeenCalledWith('#000000');

    component.clearColor();
    expect(component.selectedColor).toBeNull();
    expect(component.colorChange.emit).toHaveBeenCalledWith(null);
  });
});
