import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { FakeAuth } from '../../../testing/fake-auth';
import { AuthService } from './auth.service';
import { HasPermissionDirective } from './has-permission.directive';

@Component({
  imports: [HasPermissionDirective],
  template: `
    <button *hrmHasPermission="'role:manage'" class="single">Thêm vai trò</button>
    <a *hrmHasPermission="anyOf()" class="any">Duyệt đơn</a>
  `,
})
class Host {
  readonly anyOf = signal<readonly string[]>(['leave:approve', 'attendance:confirm']);
}

describe('HasPermissionDirective (PLT-03)', () => {
  let auth: FakeAuth;

  beforeEach(() => {
    auth = new FakeAuth();
    TestBed.configureTestingModule({
      imports: [Host],
      providers: [{ provide: AuthService, useValue: auth }],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture;
  }

  it('ẩn phần tử khi không có quyền, hiện khi có một trong các quyền', () => {
    auth.signInAs('MANAGER');
    const host = render().nativeElement as HTMLElement;

    expect(host.querySelector('.single')).toBeNull();
    expect(host.querySelector('.any')).not.toBeNull();
  });

  it('cập nhật khi quyền hoặc danh sách quyền thay đổi', () => {
    auth.signInAs('EMPLOYEE');
    const fixture = render();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.single')).toBeNull();
    expect(host.querySelector('.any')).toBeNull();

    auth.signInAs('ADMIN');
    fixture.componentInstance.anyOf.set(['user:manage']);
    fixture.detectChanges();

    expect(host.querySelector('.single')).not.toBeNull();
    expect(host.querySelectorAll('.any')).toHaveLength(1);
  });
});
